package kr.cheongmo;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import kr.cheongmo.meeting.dto.MeetingDtos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class PollIntegrationTest extends MeetingIntegrationTest {
  @Autowired PlatformTransactionManager transactions;

  @Test
  void concurrentVoteWaitsForConfirmationThenIsRejected() throws Exception {
    service.join((String) service.detail(meeting, host).inviteToken(), guest);
    String time =
        service.addOption(
            meeting, host, new MeetingDtos.OptionInput("TIME", "2026-11-01T18:00", "", "", ""));
    String place =
        service.addOption(meeting, host, new MeetingDtos.OptionInput("PLACE", "장소", "", "", ""));
    ExecutorService executor = Executors.newSingleThreadExecutor();
    CountDownLatch started = new CountDownLatch(1);
    java.util.concurrent.atomic.AtomicReference<Future<Integer>> pending =
        new java.util.concurrent.atomic.AtomicReference<>();
    try {
      new TransactionTemplate(transactions)
          .executeWithoutResult(
              tx -> {
                service.confirm(meeting, host, time, place);
                pending.set(
                    executor.submit(
                        () -> {
                          started.countDown();
                          try {
                            service.vote(meeting, guest, List.of(time));
                            return 200;
                          } catch (ResponseStatusException e) {
                            return e.getStatusCode().value();
                          }
                        }));
                try {
                  assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
                  assertThatThrownBy(() -> pending.get().get(200, TimeUnit.MILLISECONDS))
                      .isInstanceOf(TimeoutException.class);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw new AssertionError(e);
                }
              });
      assertThat(pending.get().get(3, TimeUnit.SECONDS)).isEqualTo(409);
      assertThat(db.queryForObject("SELECT COUNT(*) FROM vote", Integer.class)).isZero();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void replaceVotesRejectForeignOptionsAndFreezeAfterConfirmation() {
    service.join((String) service.detail(meeting, host).inviteToken(), guest);
    String time =
        service.addOption(
            meeting, host, new MeetingDtos.OptionInput("TIME", "2026-11-01T18:00", "", "", ""));
    String place =
        service.addOption(
            meeting,
            host,
            new MeetingDtos.OptionInput("PLACE", "예시 카페", "성수동", "https://map.kakao.com", ""));
    service.vote(meeting, guest, List.of(time, place));
    service.vote(meeting, guest, List.of(time, place));
    assertThat(db.queryForObject("SELECT COUNT(*) FROM vote", Integer.class)).isEqualTo(2);
    String other = service.create(host, new MeetingDtos.Create("다른 모임", "", "서울", 3));
    String foreign =
        service.addOption(other, host, new MeetingDtos.OptionInput("PLACE", "다른 장소", "", "", ""));
    assertThatThrownBy(() -> service.vote(meeting, guest, List.of(foreign)))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM vote", Integer.class)).isEqualTo(2);
    assertThatThrownBy(() -> service.confirm(meeting, guest, time, place))
        .isInstanceOf(ResponseStatusException.class);
    service.confirm(meeting, host, time, place);
    assertThatThrownBy(() -> service.vote(meeting, guest, List.of(time)))
        .isInstanceOf(ResponseStatusException.class);
    service.reopen(meeting, host);
    service.vote(meeting, guest, List.of());
    assertThat(db.queryForObject("SELECT COUNT(*) FROM vote", Integer.class)).isZero();
  }

  @Test
  void invalidTimeAndUnsafeUrlAreRejected() {
    assertThatThrownBy(
            () ->
                service.addOption(
                    meeting, host, new MeetingDtos.OptionInput("TIME", "not-a-date", "", "", "")))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(
            () ->
                service.addOption(
                    meeting,
                    host,
                    new MeetingDtos.OptionInput("PLACE", "장소", "", "javascript:alert(1)", "")))
        .isInstanceOf(ResponseStatusException.class);
  }
}
