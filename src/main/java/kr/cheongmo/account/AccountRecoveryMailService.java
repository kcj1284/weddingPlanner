package kr.cheongmo.account;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AccountRecoveryMailService {
  private final AccountRecoveryService recovery;
  private final RecoveryMailService mail;

  public AccountRecoveryMailService(AccountRecoveryService recovery, RecoveryMailService mail) {
    this.recovery = recovery;
    this.mail = mail;
  }

  private final java.util.concurrent.ThreadPoolExecutor mailQueue =
      new java.util.concurrent.ThreadPoolExecutor(
          2,
          2,
          0,
          java.util.concurrent.TimeUnit.SECONDS,
          new java.util.concurrent.ArrayBlockingQueue<>(100),
          task -> {
            var thread = new Thread(task, "account-recovery-mail");
            thread.setDaemon(true);
            return thread;
          });

  @jakarta.annotation.PreDestroy
  public void stop() {
    mailQueue.shutdown();
  }

  public void enqueue(Runnable action) {
    if (mail.local()) {
      action.run();
      return;
    }
    try {
      mailQueue.execute(
          () -> {
            try {
              action.run();
            } catch (Exception error) {
              org.slf4j.LoggerFactory.getLogger(AccountRecoveryMailService.class)
                  .warn("Account recovery mail request could not be completed");
            }
          });
    } catch (java.util.concurrent.RejectedExecutionException error) {
      throw AccountRecoveryService.error(
          HttpStatus.SERVICE_UNAVAILABLE, "메일 요청이 많아요. 잠시 후 다시 시도해 주세요.");
    }
  }

  public void requestReset(String address, String email) {
    recovery.limit(address, email);
    recovery.available();
    enqueue(() -> recovery.requestReset(email));
  }
}
