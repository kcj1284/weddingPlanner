package kr.cheongmo;
import java.util.*;
import java.time.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Service
@Transactional
class GuestAccess {
    private static final String CODE_ALPHABET="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final JdbcTemplate db; private final Meetings meetings; private final Accounts accounts;
    GuestAccess(JdbcTemplate db,Meetings meetings,Accounts accounts){this.db=db;this.meetings=meetings;this.accounts=accounts;}
    static String hash(String token) {
        if(token==null)throw Meetings.error(HttpStatus.NOT_FOUND,"초대코드가 잘못되었거나 만료되었습니다.");
        if(!token.matches("[A-Za-z0-9_-]{43}")) {
            token=token.replaceAll("[\\s-]","").toUpperCase(Locale.ROOT);
            if(!token.matches("(?:[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}|[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{20})"))throw Meetings.error(HttpStatus.NOT_FOUND,"초대코드가 잘못되었거나 만료되었습니다.");
        }
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    Map<String,Object> link(String token,boolean lock) {
        return db.query("SELECT * FROM guest_link WHERE token_hash=? AND revoked=0 AND expires_at>CURRENT_TIMESTAMP"+(lock?" FOR UPDATE":""),(rs,n)->{
            Map<String,Object> m=new LinkedHashMap<>();m.put("id",rs.getString("id"));m.put("meetingId",rs.getString("meeting_id"));m.put("userId",rs.getString("guest_user_id"));return m;
        },hash(token)).stream().findFirst().orElseThrow(()->Meetings.error(HttpStatus.NOT_FOUND,"초대 링크가 잘못되었거나 만료되었습니다."));
    }
    Map<String,Object> create(String id,String host,String label) {
        meetings.requireHost(id,host,true);
        if(label==null||label.isBlank()||label.length()>30)throw Meetings.error(HttpStatus.BAD_REQUEST,"초대 대상 이름은 1–30자로 입력해 주세요.");
        if(db.queryForObject("SELECT COUNT(*) FROM guest_link WHERE meeting_id=? AND revoked=0 AND expires_at>CURRENT_TIMESTAMP",Integer.class,id)>=100)
            throw Meetings.error(HttpStatus.CONFLICT,"사용 가능한 초대 링크는 모임당 최대 100개입니다.");
        String linkId=UUID.randomUUID().toString();var random=new SecureRandom();
        for(int attempt=0;attempt<10;attempt++) {
            String token;
            do {
                var generated=new StringBuilder();
                for(int i=0;i<6;i++)generated.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
                token=generated.toString();
            }while(!token.matches(".*[A-Z].*")||!token.matches(".*[2-9].*"));
            try {
                db.update("INSERT INTO guest_link(id,meeting_id,label,token_hash,expires_at) VALUES(?,?,?,?,?)",linkId,id,label.trim(),hash(token),java.sql.Timestamp.valueOf(LocalDateTime.now().plusDays(30)));
                return Map.of("id",linkId,"token",token,"code",token,"label",label.trim(),"expiresInDays",30);
            }catch(org.springframework.dao.DuplicateKeyException collision) { /* Generate another code if already issued. */ }
        }
        throw Meetings.error(HttpStatus.SERVICE_UNAVAILABLE,"초대코드를 다시 만들어 주세요.");
    }
    List<Map<String,Object>> list(String id,String host) {
        meetings.requireHost(id,host,false);
        return db.query("SELECT l.*,u.display_name FROM guest_link l LEFT JOIN app_user u ON u.id=l.guest_user_id WHERE l.meeting_id=? ORDER BY l.created_at",(rs,n)->{
            Map<String,Object> out=new LinkedHashMap<>();out.put("id",rs.getString("id"));out.put("label",rs.getString("label"));out.put("displayName",rs.getString("display_name"));out.put("revoked",rs.getInt("revoked")==1);out.put("expiresAt",rs.getTimestamp("expires_at").toLocalDateTime().toString());return out;
        },id);
    }
    Map<String,Object> preview(String token) {
        var l=link(token,false);var m=meetings.meeting((String)l.get("meetingId"),false);
        return Map.of("title",m.get("title"),"joined",l.get("userId")!=null);
    }
    Accounts.Account enter(String token,String name) {
        var l=link(token,true);String id=(String)l.get("meetingId");meetings.meeting(id,true);
        if(l.get("userId")!=null)return accounts.account(db.queryForObject("SELECT username FROM app_user WHERE id=?",String.class,l.get("userId")));
        if(name==null||name.isBlank()||name.length()>30)throw Meetings.error(HttpStatus.BAD_REQUEST,"모임에서 사용할 이름을 1–30자로 입력해 주세요.");
        if(db.queryForObject("SELECT COUNT(*) FROM member WHERE meeting_id=?",Integer.class,id)>=100)throw Meetings.error(HttpStatus.CONFLICT,"모임은 최대 100명까지 참여할 수 있습니다.");
        var guest=accounts.guest(name.strip());
        db.update("INSERT INTO member(meeting_id,user_id) VALUES(?,?)",id,guest.id());
        db.update("UPDATE guest_link SET guest_user_id=? WHERE id=?",guest.id(),l.get("id"));return guest;
    }
    void revoke(String id,String host,String linkId) {
        // Lock the link before the meeting, matching enter() to avoid lock inversion.
        var users=db.query("SELECT guest_user_id FROM guest_link WHERE id=? AND meeting_id=? FOR UPDATE",(rs,n)->rs.getString(1),linkId,id);
        meetings.requireHost(id,host,true);
        if(users.isEmpty())throw Meetings.error(HttpStatus.NOT_FOUND,"초대 링크를 찾을 수 없습니다.");
        String user=users.getFirst();
        if(user!=null){db.update("DELETE FROM vote WHERE meeting_id=? AND user_id=?",id,user);db.update("DELETE FROM member WHERE meeting_id=? AND user_id=?",id,user);}
        db.update("UPDATE guest_link SET revoked=1 WHERE id=?",linkId);
    }
}
@RestController
@RequestMapping("/api")
class GuestAccessController {
    private final GuestAccess links;private final Accounts accounts;private final GuestAttemptLimiter limiter;
    GuestAccessController(GuestAccess links,Accounts accounts,GuestAttemptLimiter limiter){this.links=links;this.accounts=accounts;this.limiter=limiter;}
    record Label(@NotBlank @Size(max=30) String label){}
    record Entry(@NotBlank String token,@Size(max=30) String displayName){}
    @PostMapping("/meetings/{id}/guest-links") Map<String,Object> create(@PathVariable String id,java.security.Principal p,@Valid @RequestBody Label in){return links.create(id,accounts.account(p.getName()).id(),in.label());}
    @GetMapping("/meetings/{id}/guest-links") List<Map<String,Object>> list(@PathVariable String id,java.security.Principal p){return links.list(id,accounts.account(p.getName()).id());}
    @DeleteMapping("/meetings/{id}/guest-links/{linkId}") Map<String,Boolean> revoke(@PathVariable String id,@PathVariable String linkId,java.security.Principal p){links.revoke(id,accounts.account(p.getName()).id(),linkId);return Map.of("revoked",true);}
    @GetMapping("/guest-preview/{token}") Map<String,Object> preview(@PathVariable String token,HttpServletRequest req){limiter.check(req.getRemoteAddr());return links.preview(token);}
    @PostMapping("/guest-session") Map<String,String> enter(@Valid @RequestBody Entry in,HttpServletRequest req,HttpServletResponse res) {
        limiter.check(req.getRemoteAddr());
        var account=links.enter(in.token(),in.displayName());
        // Replace any existing identity and rotate its session; this is a bearer-link guest session.
        var old=req.getSession(false);if(old!=null)old.invalidate();req.getSession(true);
        var principal=User.withUsername(account.username()).password("").roles("GUEST").build();
        var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of(new SimpleGrantedAuthority("ROLE_GUEST"))));
        SecurityContextHolder.setContext(context);new HttpSessionSecurityContextRepository().saveContext(context,req,res);
        var l=links.link(in.token(),false);return Map.of("meetingId",(String)l.get("meetingId"));
    }
}
