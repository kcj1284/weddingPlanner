package kr.cheongmo.account;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecoveryMailService {
  private final Environment environment;
  private final String from;
  private final Path outbox;

  public RecoveryMailService(
      Environment environment,
      @Value("${cheongmo.mail-from:}") String from,
      @Value("${cheongmo.mail-outbox:artifacts/mailbox}") String outbox) {
    this.environment = environment;
    this.from = from;
    this.outbox = Path.of(outbox);
  }

  public boolean local() {
    return transport().equals("smtp")
        && environment.matchesProfiles("local", "test")
        && environment.getProperty("spring.mail.host", "").isBlank();
  }

  public boolean ready() {
    if (transport().equals("brevo"))
      return !environment.getProperty("cheongmo.brevo-api-key", "").isBlank() && !from.isBlank();
    return transport().equals("smtp")
        && (local() || (!environment.getProperty("spring.mail.host", "").isBlank() && !from.isBlank()));
  }

  private String transport() {
    return environment.getProperty("cheongmo.mail-transport", "smtp");
  }

  private void sendWithBrevo(String to, String subject, String text) {
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    factory.setReadTimeout(Duration.ofSeconds(5));
    String baseUrl = environment.getProperty("cheongmo.brevo-api-url", "https://api.brevo.com");
    // A loopback test server can verify the protocol without sending real mail.
    if (!baseUrl.equals("https://api.brevo.com") && !environment.matchesProfiles("test"))
      throw new IllegalArgumentException("Unexpected transactional mail endpoint");
    RestClient.builder()
        .requestFactory(factory)
        .baseUrl(baseUrl)
        .build()
        .post()
        .uri("/v3/smtp/email")
        .header("api-key", environment.getRequiredProperty("cheongmo.brevo-api-key"))
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "sender",
                Map.of("name", "청모", "email", from),
                "to",
                List.of(Map.of("email", to)),
                "subject",
                subject,
                "textContent",
                text))
        .retrieve()
        .toBodilessEntity();
  }

  public void send(String to, String subject, String text) {
    if (!ready())
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "메일 발송 설정이 필요합니다. 잠시 후 다시 시도해 주세요.");
    try {
      if (transport().equals("brevo")) {
        sendWithBrevo(to, subject, text);
        return;
      }
      if (local()) {
        Files.createDirectories(outbox);
        Files.writeString(
            outbox.resolve(UUID.randomUUID() + ".eml"),
            "To: "
                + to
                + "\nSubject: "
                + subject
                + "\nContent-Type: text/plain; charset=UTF-8\n\n"
                + text,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW);
        return;
      }
      var sender = new JavaMailSenderImpl();
      sender.setHost(environment.getRequiredProperty("spring.mail.host"));
      sender.setPort(environment.getProperty("spring.mail.port", Integer.class, 587));
      sender.setUsername(environment.getProperty("spring.mail.username"));
      sender.setPassword(environment.getProperty("spring.mail.password"));
      var props = sender.getJavaMailProperties();
      props.setProperty(
          "mail.smtp.auth", environment.getProperty("spring.mail.properties.mail.smtp.auth", "true"));
      props.setProperty("mail.smtp.ssl.checkserveridentity", "true");
      props.setProperty(
          "mail.smtp.starttls.enable",
          environment.getProperty("spring.mail.properties.mail.smtp.starttls.enable", "true"));
      props.setProperty(
          "mail.smtp.starttls.required",
          environment.getProperty("spring.mail.properties.mail.smtp.starttls.required", "true"));
      props.setProperty(
          "mail.smtp.ssl.enable",
          environment.getProperty("spring.mail.properties.mail.smtp.ssl.enable", "false"));
      props.setProperty("mail.smtp.connectiontimeout", "5000");
      props.setProperty("mail.smtp.timeout", "5000");
      props.setProperty("mail.smtp.writetimeout", "5000");
      var message = new SimpleMailMessage();
      message.setFrom(from);
      message.setTo(to);
      message.setSubject(subject);
      message.setText(text);
      sender.send(message);
    } catch (Exception error) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "메일 발송이 지연되고 있어요. 잠시 후 다시 시도해 주세요.");
    }
  }
}

