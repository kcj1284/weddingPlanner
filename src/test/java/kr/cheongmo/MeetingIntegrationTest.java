package kr.cheongmo;

import static org.assertj.core.api.Assertions.*;

import kr.cheongmo.account.AccountService;
import kr.cheongmo.account.dto.AccountDtos;
import kr.cheongmo.meeting.MeetingService;
import kr.cheongmo.meeting.dto.MeetingDtos;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
class MeetingIntegrationTest {
  @Autowired MeetingService service;
  @Autowired AccountService accounts;
  @Autowired JdbcTemplate db;
  String host, guest, outsider, meeting;

  @BeforeEach
  void prepare() {
    if (!db.queryForObject("SELECT current_database()", String.class).endsWith("_test"))
      throw new IllegalStateException("통합 테스트는 _test로 끝나는 전용 DB에서만 실행합니다.");
    db.update("DELETE FROM guest_link");
    db.update("DELETE FROM social_identity");
    db.update("DELETE FROM account_kind");
    db.update("DELETE FROM vote");
    db.update("DELETE FROM poll_option");
    db.update("DELETE FROM member");
    db.update("DELETE FROM meeting");
    db.update("DELETE FROM app_user");
    host = accounts.register(new AccountDtos.Registration("host", "호스트", "password123")).id();
    guest = accounts.register(new AccountDtos.Registration("guest", "게스트", "password123")).id();
    outsider = accounts.register(new AccountDtos.Registration("outside", "외부", "password123")).id();
    meeting = service.create(host, new MeetingDtos.Create("우리 청모", "함께 만나요", "서울 성수", 6));
  }

  @Test
  void joinIsIdempotentAndRolesAreMeetingScoped() {
    String token = (String) service.detail(meeting, host).inviteToken();
    service.join(token, guest);
    service.join(token, guest);
    assertThat(service.detail(meeting, guest).role()).isEqualTo("GUEST");
    assertThat(service.detail(meeting, host).memberCount()).isEqualTo(2);
    assertThatThrownBy(() -> service.detail(meeting, outsider))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(
            () ->
                service.addOption(
                    meeting,
                    guest,
                    new MeetingDtos.OptionInput("TIME", "2026-11-01T18:00", "", "", "")))
        .isInstanceOf(ResponseStatusException.class);
  }
}
