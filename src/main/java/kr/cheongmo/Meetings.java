package kr.cheongmo;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.net.URI;
import java.security.SecureRandom;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
class Meetings {
    private final JdbcTemplate db;
    Meetings(JdbcTemplate db) { this.db=db; }
    record Create(@NotBlank @Size(max=80) String title,@Size(max=500) String description,
        @NotBlank @Size(max=80) String area,@Min(2) @Max(100) int expectedCount) {}
    record OptionInput(@NotNull @Pattern(regexp="TIME|PLACE") String kind,
        @NotBlank @Size(max=120) String title,@Size(max=300) String detail,
        @Size(max=500) String mapUrl,@Size(max=40) String phone) {}
    static ResponseStatusException error(HttpStatus code,String text) { return new ResponseStatusException(code,text); }
    static String text(String value) { return value==null?"":value.trim(); }
    String create(String user,Create in) {
        if(db.queryForObject("SELECT COUNT(*) FROM account_kind WHERE user_id=? AND kind='GUEST'",Integer.class,user)>0)
            throw error(HttpStatus.FORBIDDEN,"모임을 열려면 호스트 계정으로 가입·로그인해 주세요.");
        if (text(in.title()).isEmpty()||in.title().length()>80||text(in.area()).isEmpty()||in.area().length()>80||in.expectedCount()<2||in.expectedCount()>100||text(in.description()).length()>500)
            throw error(HttpStatus.BAD_REQUEST,"모임 이름·지역·예상 인원을 확인해 주세요.");
        String id=UUID.randomUUID().toString(); byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        db.update("INSERT INTO meeting(id,host_id,title,description,area,expected_count,invite_token) VALUES(?,?,?,?,?,?,?)",id,user,text(in.title()),text(in.description()),text(in.area()),in.expectedCount(),token);
        db.update("INSERT INTO member(meeting_id,user_id) VALUES(?,?)",id,user); return id;
    }
    List<Map<String,Object>> list(String user) {
        return db.query("SELECT m.*, (SELECT COUNT(*) FROM member x WHERE x.meeting_id=m.id) member_count FROM meeting m JOIN member mem ON mem.meeting_id=m.id WHERE mem.user_id=? ORDER BY m.created_at DESC",
            (rs,n)->{Map<String,Object> m=new LinkedHashMap<>();m.put("id",rs.getString("id"));m.put("title",rs.getString("title"));m.put("area",rs.getString("area"));m.put("status",rs.getString("status"));m.put("role",user.equals(rs.getString("host_id"))?"HOST":"GUEST");m.put("memberCount",rs.getInt("member_count")); return m;},user);
    }
    Map<String,Object> meeting(String id,boolean lock) {
        return db.query("SELECT * FROM meeting WHERE id=?"+(lock?" FOR UPDATE":""),(rs,n)->{
            Map<String,Object> m=new LinkedHashMap<>();
            for(String col:List.of("id","host_id","title","description","area","invite_token","status","final_time_id","final_place_id")) m.put(col,rs.getString(col));
            m.put("expectedCount",rs.getInt("expected_count"));return m;
        },id).stream().findFirst().orElseThrow(()->error(HttpStatus.NOT_FOUND,"모임을 찾을 수 없습니다."));
    }
    void requireMember(String id,String user) {
        if(db.queryForObject("SELECT COUNT(*) FROM member WHERE meeting_id=? AND user_id=?",Integer.class,id,user)==0)
            throw error(HttpStatus.FORBIDDEN,"초대받은 모임에 먼저 참여해 주세요.");
    }
    Map<String,Object> requireHost(String id,String user,boolean lock) {
        Map<String,Object> m=meeting(id,lock);
        if(!user.equals(m.get("host_id"))) throw error(HttpStatus.FORBIDDEN,"호스트만 사용할 수 있습니다."); return m;
    }
    void open(Map<String,Object> m) { if(!"OPEN".equals(m.get("status"))) throw error(HttpStatus.CONFLICT,"확정된 모임입니다. 호스트가 투표를 다시 열어야 합니다."); }
    String join(String token,String user) {
        if(db.queryForObject("SELECT COUNT(*) FROM account_kind WHERE user_id=? AND kind='GUEST'",Integer.class,user)>0)
            throw error(HttpStatus.FORBIDDEN,"게스트는 받은 전용 링크로 참여해 주세요.");
        String id=db.query("SELECT id FROM meeting WHERE invite_token=?",(rs,n)->rs.getString(1),token).stream().findFirst()
            .orElseThrow(()->error(HttpStatus.NOT_FOUND,"초대 링크를 확인해 주세요."));
        meeting(id,true);
        if(db.queryForObject("SELECT COUNT(*) FROM member WHERE meeting_id=? AND user_id=?",Integer.class,id,user)==0) {
            if(db.queryForObject("SELECT COUNT(*) FROM member WHERE meeting_id=?",Integer.class,id)>=100) throw error(HttpStatus.CONFLICT,"모임은 최대 100명까지 참여할 수 있습니다.");
            db.update("INSERT INTO member(meeting_id,user_id) VALUES(?,?)",id,user);
        } return id;
    }
    Map<String,Object> detail(String id,String user) {
        requireMember(id,user); Map<String,Object> m=meeting(id,false); Map<String,Object> out=new LinkedHashMap<>();
        for(String key:List.of("id","title","description","area","status","expectedCount")) out.put(key,m.get(key));
        out.put("role",user.equals(m.get("host_id"))?"HOST":"GUEST");if(user.equals(m.get("host_id")))out.put("inviteToken",m.get("invite_token"));
        out.put("finalTimeId",m.get("final_time_id"));out.put("finalPlaceId",m.get("final_place_id"));
        out.put("hostName",db.queryForObject("SELECT display_name FROM app_user WHERE id=?",String.class,m.get("host_id")));
        out.put("memberCount",db.queryForObject("SELECT COUNT(*) FROM member WHERE meeting_id=?",Integer.class,id));
        out.put("responseCount",db.queryForObject("SELECT COUNT(DISTINCT user_id) FROM vote WHERE meeting_id=?",Integer.class,id));
        out.put("myVotes",db.query("SELECT option_id FROM vote WHERE meeting_id=? AND user_id=?",(rs,n)->rs.getString(1),id,user));
        out.put("options",db.query("SELECT o.*, (SELECT COUNT(*) FROM vote v WHERE v.option_id=o.id) vote_count FROM poll_option o WHERE o.meeting_id=? ORDER BY o.created_at,o.id",(rs,n)->{
            Map<String,Object> o=new LinkedHashMap<>();
            o.put("id",rs.getString("id"));o.put("kind",rs.getString("kind"));o.put("title",rs.getString("title"));o.put("detail",rs.getString("detail"));o.put("mapUrl",rs.getString("map_url"));o.put("phone",rs.getString("phone"));o.put("reservation",rs.getString("reservation"));o.put("reservationNote",rs.getString("reservation_note"));o.put("votes",rs.getInt("vote_count"));return o;
        },id)); return out;
    }
    String addOption(String id,String user,OptionInput in) {
        open(requireHost(id,user,true));
        if(!List.of("TIME","PLACE").contains(in.kind())||text(in.title()).isEmpty()||in.title().length()>120||text(in.detail()).length()>300||text(in.phone()).length()>40||text(in.mapUrl()).length()>500)
            throw error(HttpStatus.BAD_REQUEST,"후보 정보를 확인해 주세요.");
        if("TIME".equals(in.kind())) { try { LocalDateTime.parse(in.title()); } catch(DateTimeParseException e) { throw error(HttpStatus.BAD_REQUEST,"날짜와 시간을 확인해 주세요."); } }
        if(!text(in.mapUrl()).isEmpty()) {
            try { URI url=URI.create(in.mapUrl());if(!List.of("http","https").contains(url.getScheme())||url.getHost()==null||url.getUserInfo()!=null) throw new IllegalArgumentException(); }
            catch(IllegalArgumentException e) { throw error(HttpStatus.BAD_REQUEST,"지도 링크는 올바른 https 또는 http 주소여야 합니다."); }
        }
        if(db.queryForObject("SELECT COUNT(*) FROM poll_option WHERE meeting_id=? AND kind=?",Integer.class,id,in.kind())>=30) throw error(HttpStatus.CONFLICT,"종류별 후보는 최대 30개입니다.");
        if(db.queryForObject("SELECT COUNT(*) FROM poll_option WHERE meeting_id=? AND kind=? AND title=? AND NVL(detail,' ')=?",Integer.class,id,in.kind(),text(in.title()),text(in.detail()).isEmpty()?" ":text(in.detail()))>0) throw error(HttpStatus.CONFLICT,"같은 후보가 이미 있습니다.");
        String option=UUID.randomUUID().toString();
        db.update("INSERT INTO poll_option(id,meeting_id,kind,title,detail,map_url,phone) VALUES(?,?,?,?,?,?,?)",option,id,in.kind(),text(in.title()),text(in.detail()),text(in.mapUrl()),text(in.phone()));return option;
    }
    void vote(String id,String user,List<String> choices) {
        requireMember(id,user);open(meeting(id,true));
        if(choices==null||choices.size()>60||new HashSet<>(choices).size()!=choices.size()) throw error(HttpStatus.BAD_REQUEST,"중복되거나 너무 많은 선택입니다.");
        for(String choice:choices) if(db.queryForObject("SELECT COUNT(*) FROM poll_option WHERE id=? AND meeting_id=?",Integer.class,choice,id)==0) throw error(HttpStatus.BAD_REQUEST,"이 모임의 후보만 선택할 수 있습니다.");
        db.update("DELETE FROM vote WHERE meeting_id=? AND user_id=?",id,user);
        for(String choice:choices) db.update("INSERT INTO vote(meeting_id,user_id,option_id) VALUES(?,?,?)",id,user,choice);
    }
    void confirm(String id,String user,String time,String place) {
        open(requireHost(id,user,true));
        if(db.queryForObject("SELECT COUNT(*) FROM poll_option WHERE id=? AND meeting_id=? AND kind='TIME'",Integer.class,time,id)!=1 ||
           db.queryForObject("SELECT COUNT(*) FROM poll_option WHERE id=? AND meeting_id=? AND kind='PLACE'",Integer.class,place,id)!=1)
            throw error(HttpStatus.BAD_REQUEST,"이 모임의 시간 하나와 장소 하나를 선택해 주세요.");
        db.update("UPDATE meeting SET status='CONFIRMED',final_time_id=?,final_place_id=? WHERE id=?",time,place,id);
    }
    void reopen(String id,String user) {requireHost(id,user,true);db.update("UPDATE meeting SET status='OPEN',final_time_id=NULL,final_place_id=NULL WHERE id=?",id);}
    void reservation(String id,String user,String option,String status,String note) {
        requireHost(id,user,true);
        if(!List.of("UNKNOWN","AVAILABLE","BOOKED").contains(status)||text(note).length()>300) throw error(HttpStatus.BAD_REQUEST,"예약 확인 상태와 메모를 확인해 주세요.");
        if(db.update("UPDATE poll_option SET reservation=?,reservation_note=? WHERE id=? AND meeting_id=? AND kind='PLACE'",status,text(note),option,id)!=1) throw error(HttpStatus.NOT_FOUND,"장소 후보를 찾을 수 없습니다.");
    }
}
