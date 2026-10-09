package kr.cheongmo;
import java.util.*;
import java.nio.charset.StandardCharsets;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.core.env.Environment;
import java.security.Principal;
import org.springframework.transaction.annotation.Transactional;

@Service
class Accounts implements UserDetailsService {
    private final JdbcTemplate db; private final PasswordEncoder encoder;
    Accounts(JdbcTemplate db, PasswordEncoder encoder) { this.db = db; this.encoder = encoder; }
    record Account(String id, String username, String displayName, boolean guest) {}
    record Registration(@Pattern(regexp="[a-z0-9_]{3,30}") @NotNull String username,
        @NotBlank @Size(max=30) String displayName, @NotNull @Size(min=8,max=72) String password) {}
    Account register(Registration body) {
        if (body.password().getBytes(StandardCharsets.UTF_8).length > 72) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"비밀번호는 72바이트 이하여야 합니다.");
        String id = UUID.randomUUID().toString();
        db.update("INSERT INTO app_user(id,username,display_name,password_hash) VALUES(?,?,?,?)",id,body.username(),body.displayName().trim(),encoder.encode(body.password()));
        return new Account(id,body.username(),body.displayName().trim(),false);
    }
    Account account(String name) {
        return db.query("SELECT u.id,u.username,u.display_name,CASE WHEN k.kind='GUEST' THEN 1 ELSE 0 END is_guest FROM app_user u LEFT JOIN account_kind k ON k.user_id=u.id WHERE username=?", (rs,n)->new Account(rs.getString(1),rs.getString(2),rs.getString(3),rs.getInt(4)==1),name)
            .stream().findFirst().orElseThrow(()->new UsernameNotFoundException("계정을 확인해 주세요."));
    }
    @Override public UserDetails loadUserByUsername(String name) {
        return db.query("SELECT username,password_hash FROM app_user u WHERE username=? AND NOT EXISTS(SELECT 1 FROM account_kind k WHERE k.user_id=u.id AND k.kind='GUEST')", (rs,n)->User.withUsername(rs.getString(1)).password(rs.getString(2)).roles("USER").build(),name)
            .stream().findFirst().orElseThrow(()->new UsernameNotFoundException("계정을 확인해 주세요."));
    }
    @Transactional
    Account guest(String name) {
        Account a=register(new Registration("g_"+UUID.randomUUID().toString().replace("-","").substring(0,26),name,UUID.randomUUID().toString()));
        db.update("INSERT INTO account_kind(user_id,kind) VALUES(?,'GUEST')",a.id());
        return new Account(a.id(),a.username(),a.displayName(),true);
    }
    @Transactional
    Account social(String provider,String subject,String name) {
        var found=db.query("SELECT u.username FROM app_user u JOIN social_identity s ON s.user_id=u.id WHERE s.provider=? AND s.subject=?",(rs,n)->rs.getString(1),provider,subject);
        if(!found.isEmpty())return account(found.getFirst());
        String display=(name==null||name.isBlank())?"호스트":name.strip();
        display=display.substring(0,Math.min(display.length(),30));
        Account a=register(new Registration("s_"+UUID.randomUUID().toString().replace("-","").substring(0,26),display,UUID.randomUUID().toString()));
        db.update("INSERT INTO social_identity(provider,subject,user_id) VALUES(?,?,?)",provider,subject,a.id());return a;
    }
}
@RestController
@RequestMapping("/api")
class AccountController {
    private final Accounts accounts; private final Environment env;
    AccountController(Accounts accounts, Environment env) { this.accounts=accounts; this.env=env; }
    @GetMapping("/csrf") Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
    @GetMapping("/config") Map<String,Boolean> config() { return Map.of("demoEnabled",env.matchesProfiles("local")); }
    @GetMapping("/me") Accounts.Account me(Principal principal) { return accounts.account(principal.getName()); }
    @PostMapping("/register") Accounts.Account register(@Valid @RequestBody Accounts.Registration body) { return accounts.register(body); }
}
