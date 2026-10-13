package kr.cheongmo;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;
import kr.cheongmo.guest.GuestAttemptLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class GuestAttemptLimiterTest {
  @Test
  void attemptsAreBoundedPerAddressAndRecoverAfterOneMinute() {
    var time = new AtomicLong(1000);
    var limiter = new GuestAttemptLimiter(time::get);
    for (int i = 0; i < 30; i++) limiter.check("guest-a");
    assertThatThrownBy(() -> limiter.check("guest-a"))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(429));
    assertThatCode(() -> limiter.check("guest-b")).doesNotThrowAnyException();
    time.addAndGet(60_000);
    assertThatCode(() -> limiter.check("guest-a")).doesNotThrowAnyException();
  }
}
