package kr.cheongmo;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import kr.cheongmo.account.RecoveryMailService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

class RecoveryMailHttpTest {
  @Test
  void sendsBrevoJsonThroughHttpInsteadOfSmtp() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var received = new AtomicReference<String>();
    var apiKey = new AtomicReference<String>();
    server.createContext(
        "/v3/smtp/email",
        exchange -> {
          apiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
          received.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          exchange.sendResponseHeaders(201, -1);
          exchange.close();
        });
    server.start();
    try {
      var env = new MockEnvironment();
      env.setActiveProfiles("test");
      env.setProperty("cheongmo.mail-transport", "brevo");
      env.setProperty("cheongmo.brevo-api-key", "synthetic-api-key");
      env.setProperty(
          "cheongmo.brevo-api-url", "http://127.0.0.1:" + server.getAddress().getPort());
      var mail =
          new RecoveryMailService(env, "sender@example.test", "artifacts/unused-http-outbox");
      assertThat(mail.ready()).isTrue();
      assertThat(mail.local()).isFalse();
      mail.send("recipient@example.test", "[청모] 테스트", "인증 링크를 확인해 주세요.");
      var json = new tools.jackson.databind.ObjectMapper().readTree(received.get());
      assertThat(apiKey.get()).isEqualTo("synthetic-api-key");
      assertThat(json.path("sender").path("email").asString()).isEqualTo("sender@example.test");
      assertThat(json.path("to").get(0).path("email").asString())
          .isEqualTo("recipient@example.test");
      assertThat(json.path("textContent").asString()).isEqualTo("인증 링크를 확인해 주세요.");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void missingApiKeyDoesNotSilentlyFallBackToLocalMailbox() {
    var env = new MockEnvironment();
    env.setActiveProfiles("test");
    env.setProperty("cheongmo.mail-transport", "brevo");
    var mail = new RecoveryMailService(env, "sender@example.test", "artifacts/unused-http-outbox");
    assertThat(mail.local()).isFalse();
    assertThat(mail.ready()).isFalse();
    assertThatThrownBy(() -> mail.send("recipient@example.test", "subject", "text"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void providerFailureIsReportedWithoutApiKeyOrRecoveryLink() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v3/smtp/email",
        exchange -> {
          exchange.sendResponseHeaders(503, -1);
          exchange.close();
        });
    server.start();
    try {
      var env = new MockEnvironment();
      env.setActiveProfiles("test");
      env.setProperty("cheongmo.mail-transport", "brevo");
      env.setProperty("cheongmo.brevo-api-key", "synthetic-api-key");
      env.setProperty(
          "cheongmo.brevo-api-url", "http://127.0.0.1:" + server.getAddress().getPort());
      var mail =
          new RecoveryMailService(env, "sender@example.test", "artifacts/unused-http-outbox");
      assertThatThrownBy(
              () -> mail.send("recipient@example.test", "subject", "secret-recovery-link"))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageNotContaining("synthetic-api-key")
          .hasMessageNotContaining("secret-recovery-link");
    } finally {
      server.stop(0);
    }
  }
}
