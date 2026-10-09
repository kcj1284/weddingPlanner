package kr.cheongmo;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:tests;MODE=Oracle;DB_CLOSE_DELAY=-1", "spring.datasource.username=sa", "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:schema-local.sql"})
@ActiveProfiles("test")
class MeetingIntegrationTest {
    @Autowired Meetings service; @Autowired Accounts accounts; @Autowired JdbcTemplate db;
    String host, guest, outsider, meeting;
    @BeforeEach void prepare() {
        db.update("DELETE FROM guest_link");db.update("DELETE FROM social_identity");db.update("DELETE FROM account_kind");
        db.update("DELETE FROM vote"); db.update("DELETE FROM poll_option"); db.update("DELETE FROM member");db.update("DELETE FROM meeting");db.update("DELETE FROM app_user");
        host=accounts.register(new Accounts.Registration("host","호스트","password123")).id();
        guest=accounts.register(new Accounts.Registration("guest","게스트","password123")).id();
        outsider=accounts.register(new Accounts.Registration("outside","외부","password123")).id();
        meeting=service.create(host,new Meetings.Create("우리 청모","함께 만나요","서울 성수",6));
    }
    @Test void joinIsIdempotentAndRolesAreMeetingScoped() {
        String token=(String)service.detail(meeting,host).get("inviteToken");
        service.join(token,guest);service.join(token,guest);
        assertThat(service.detail(meeting,guest).get("role")).isEqualTo("GUEST");
        assertThat(service.detail(meeting,host).get("memberCount")).isEqualTo(2);
        assertThatThrownBy(()->service.detail(meeting,outsider)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.addOption(meeting,guest,new Meetings.OptionInput("TIME","2026-11-01T18:00","", "", ""))).isInstanceOf(ResponseStatusException.class);
    }
}
