package kr.cheongmo.config;

import java.time.*;
import java.util.List;
import kr.cheongmo.account.AccountService;
import kr.cheongmo.account.dto.AccountDtos;
import kr.cheongmo.meeting.MeetingService;
import kr.cheongmo.meeting.dto.MeetingDtos;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "cheongmo.seed-demo",
    havingValue = "true",
    matchIfMissing = true)
public class DemoData implements CommandLineRunner {
  private final AccountService accountService;
  private final MeetingService meetingService;
  private final JdbcTemplate jdbcTemplate;

  public DemoData(AccountService a, MeetingService m, JdbcTemplate d) {
    accountService = a;
    meetingService = m;
    jdbcTemplate = d;
  }

  @Override
  public void run(String... args) {
    if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user WHERE username='demo_host'", Integer.class)
        > 0) return;
    String host =
        accountService.register(new AccountDtos.Registration("demo_host", "지은", "cheongmo-demo")).id();
    String guest =
        accountService.register(new AccountDtos.Registration("demo_guest", "수민", "cheongmo-demo")).id();
    String id =
        meetingService.create(
            host,
            new MeetingDtos.Create(
                "우리, 청첩장 나오는 날", "좋은 소식을 가장 먼저 전하고 싶은 사람들. 맛있는 식사와 함께 만나요!", "서울 성수", 6));
    meetingService.join((String) meetingService.detail(id, host).inviteToken(), guest);
    LocalDate day = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(14);
    String t1 =
        meetingService.addOption(
            id,
            host,
            new MeetingDtos.OptionInput("TIME", day.atTime(18, 30).toString(), "퇴근 후 저녁", "", ""));
    String t2 =
        meetingService.addOption(
            id,
            host,
            new MeetingDtos.OptionInput(
                "TIME", day.plusDays(1).atTime(12, 0).toString(), "여유로운 점심", "", ""));
    meetingService.addOption(
        id,
        host,
        new MeetingDtos.OptionInput(
            "TIME", day.plusDays(2).atTime(17, 0).toString(), "조금 이른 저녁", "", ""));
    String place =
        meetingService.addOption(
            id,
            host,
            new MeetingDtos.OptionInput(
                "PLACE", "예시 · 따뜻한 이탈리안 식당", "성수역 근처 / 장소 입력 예시이며 실제 업체가 아닙니다.", "", ""));
    meetingService.addOption(
        id,
        host,
        new MeetingDtos.OptionInput(
            "PLACE", "예시 · 조용한 브런치 카페", "서울숲 근처 / 실제 예약 정보는 직접 확인해 주세요.", "", ""));
    meetingService.vote(id, guest, List.of(t1, t2, place));
  }
}
