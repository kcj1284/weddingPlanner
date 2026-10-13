package kr.cheongmo.account;

import jakarta.servlet.http.*;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;
import kr.cheongmo.account.dto.RecoveryDtos;
import kr.cheongmo.common.CredentialPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountRecoveryService {
  private final AccountRecoveryRepository repository;
  private final AccountService accountService;
  private final PasswordEncoder passwordEncoder;
  private final RecoveryMailService mail;
  private final String baseUrl;

  public AccountRecoveryService(
      AccountRecoveryRepository repository,
      AccountService accountService,
      PasswordEncoder passwordEncoder,
      RecoveryMailService mail,
      @Value("${cheongmo.public-url:http://localhost:8080}") String baseUrl) {
    this.repository = repository;
    this.accountService = accountService;
    this.passwordEncoder = passwordEncoder;
    this.mail = mail;
    this.baseUrl = baseUrl.replaceAll("/+$", "");
  }

  public static String email(String value) {
    return value.strip().toLowerCase(Locale.ROOT);
  }

  public static String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public void available() {
    if (!mail.ready()) throw error(HttpStatus.SERVICE_UNAVAILABLE, "인증 메일 발송 설정이 필요합니다.");
  }

  public boolean eligible(String id) {
    return repository.countEligibleAccount(id) > 0;
  }

  public RecoveryDtos.Status status(String username) {
    var account = accountService.account(username);
    if (!eligible(account.id()))
      throw error(HttpStatus.FORBIDDEN, "일반 호스트 계정에서 복구 이메일을 설정할 수 있어요. 소셜 계정은 가입한 서비스로 로그인해 주세요.");
    var contacts = repository.findContact(account.id());
    return new RecoveryDtos.Status(
        !contacts.isEmpty(), contacts.isEmpty() ? "" : (String) contacts.getFirst().get("email"));
  }

  public void limit(String address, String contact) {
    limitBucket("ip:" + address, 20);
    limitBucket("email:" + email(contact), 5);
  }

  private void limitBucket(String key, int maximum) {
    int attempts = repository.incrementRequestAttempts(digest(key));
    if (attempts > maximum) throw error(HttpStatus.TOO_MANY_REQUESTS, "요청이 많아요. 15분 후 다시 시도해 주세요.");
  }

  private String issue(String id, String purpose, String contact) {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    repository.invalidatePurposeTokens(id, purpose);
    repository.insertToken(
        digest(token),
        id,
        purpose,
        contact,
        OffsetDateTime.now().plusMinutes(purpose.equals("VERIFY") ? 30 : 15));
    return baseUrl + "/account.html#" + purpose.toLowerCase(Locale.ROOT) + "=" + token;
  }

  @Transactional
  public void requestVerification(String username, String contact, String password) {
    available();
    String id = accountService.account(username).id();
    if (!eligible(id)) throw error(HttpStatus.FORBIDDEN, "일반 호스트 계정에서 설정해 주세요.");
    repository.lockAccount(id);
    if (password == null || !passwordEncoder.matches(password, repository.findPasswordHash(id)))
      throw error(HttpStatus.BAD_REQUEST, "현재 비밀번호를 확인해 주세요.");
    mail.send(
        email(contact),
        "[청모] 복구 이메일 인증",
        "복구 이메일을 인증하려면 아래 링크를 열어 확인해 주세요.\n"
            + issue(id, "VERIFY", email(contact))
            + "\n30분 동안 유효합니다. 요청하지 않았다면 무시해 주세요.");
  }

  @Transactional
  public void requestReset(String contact) {
    available();
    var contacts = repository.findContactOwner(email(contact));
    if (contacts.isEmpty()) return;
    String id = (String) contacts.getFirst().get("user_id");
    if (!eligible(id)) return;
    repository.lockAccount(id);
    mail.send(
        email(contact),
        "[청모] 비밀번호 재설정",
        "새 비밀번호를 설정하려면 아래 링크를 열어 주세요.\n"
            + issue(id, "RESET", email(contact))
            + "\n15분 동안 유효하며 한 번만 사용할 수 있습니다. 요청하지 않았다면 무시해 주세요.");
  }

  public RecoveryDtos.FoundId findId(String contact) {
    var contacts = repository.findUsernameByContact(email(contact));
    if (contacts.isEmpty() || !eligible((String) contacts.getFirst().get("id")))
      return new RecoveryDtos.FoundId("", "이메일과 일치하는 아이디를 찾지 못했어요. 인증한 복구 이메일을 확인해 주세요.");
    String username = (String) contacts.getFirst().get("username");
    int visible = Math.max(0, username.length() - 4);
    return new RecoveryDtos.FoundId(
        username.substring(0, visible) + "*".repeat(username.length() - visible),
        "아이디를 찾았어요. 끝 4자리는 보호를 위해 가렸어요.");
  }

  private Map<String, Object> token(String raw, String purpose) {
    var owners = repository.findTokenOwner(digest(raw));
    if (!owners.isEmpty()) repository.lockAccount(owners.getFirst().get("user_id"));
    var rows = repository.findUsableToken(digest(raw), purpose);
    if (rows.isEmpty())
      throw error(HttpStatus.BAD_REQUEST, "링크가 만료되었거나 이미 사용되었습니다. 새 메일을 요청해 주세요.");
    return rows.getFirst();
  }

  @Transactional
  public void verify(String raw) {
    var token = token(raw, "VERIFY");
    String id = (String) token.get("user_id"), contact = (String) token.get("email");
    repository.lockAccount(id);
    if (!eligible(id)) throw error(HttpStatus.FORBIDDEN, "이 계정에서는 인증할 수 없습니다.");
    repository.saveContact(id, contact);
    repository.invalidateAllTokens(id);
  }

  @Transactional
  public void reset(String raw, String password) {
    CredentialPolicy.password(password);
    var token = token(raw, "RESET");
    String id = (String) token.get("user_id");
    repository.lockAccount(id);
    if (!eligible(id) || repository.countMatchingContact(id, token.get("email")) == 0)
      throw error(HttpStatus.BAD_REQUEST, "복구 이메일이 변경되었습니다. 새 메일을 요청해 주세요.");
    repository.updatePasswordAndVersion(passwordEncoder.encode(password), id);
    repository.invalidateAllTokens(id);
  }

  public static ResponseStatusException error(HttpStatus status, String message) {
    return new ResponseStatusException(status, message);
  }
}
