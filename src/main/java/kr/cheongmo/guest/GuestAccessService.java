package kr.cheongmo.guest;

import jakarta.servlet.http.*;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import kr.cheongmo.account.AccountService;
import kr.cheongmo.account.dto.AccountDtos;
import kr.cheongmo.meeting.MeetingService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Service
@Transactional
public class GuestAccessService {
  private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

  public String candidateCode() {
    var random = new SecureRandom();
    String token;
    do {
      var generated = new StringBuilder();
      for (int i = 0; i < 6; i++)
        generated.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
      token = generated.toString();
    } while (!token.matches(".*[A-Z].*") || !token.matches(".*[2-9].*"));
    return token;
  }

  private final GuestAccessRepository repository;
  private final MeetingService meetingService;
  private final AccountService accountService;
  private final InviteCodeCipher cipher;

  public GuestAccessService(
      GuestAccessRepository repository,
      MeetingService meetingService,
      AccountService accountService,
      InviteCodeCipher cipher) {
    this.repository = repository;
    this.meetingService = meetingService;
    this.accountService = accountService;
    this.cipher = cipher;
  }

  public void lockCode(String code) {
    repository.lockInviteCode(hash(code));
  }

  public static String hash(String token) {
    if (token == null) throw MeetingService.error(HttpStatus.NOT_FOUND, "초대코드가 잘못되었거나 만료되었습니다.");
    if (!token.matches("[A-Za-z0-9_-]{43}")) {
      token = token.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
      if (!token.matches(
          "(?:[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}|[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{20})"))
        throw MeetingService.error(HttpStatus.NOT_FOUND, "초대코드가 잘못되었거나 만료되었습니다.");
    }
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public Map<String, Object> link(String token, boolean lock) {
    return repository.findUsableLink(lock, hash(token)).stream()
        .findFirst()
        .orElseThrow(() -> MeetingService.error(HttpStatus.NOT_FOUND, "초대 링크가 잘못되었거나 만료되었습니다."));
  }

  public Map<String, Object> create(String id, String host, String label) {
    meetingService.requireHost(id, host, true);
    if (label == null || label.isBlank() || label.length() > 30)
      throw MeetingService.error(HttpStatus.BAD_REQUEST, "초대 대상 이름은 1–30자로 입력해 주세요.");
    if (repository.countActiveLinks(id) >= 100)
      throw MeetingService.error(HttpStatus.CONFLICT, "사용 가능한 초대 링크는 모임당 최대 100개입니다.");
    String linkId = UUID.randomUUID().toString();
    for (int attempt = 0; attempt < 10; attempt++) {
      String token = candidateCode();
      lockCode(token);
      if (repository.insertLink(
              linkId,
              id,
              label.trim(),
              hash(token),
              OffsetDateTime.now(ZoneOffset.UTC).plusDays(30),
              cipher.encrypt(token, linkId))
          == 1) {
        return Map.of(
            "id",
            linkId,
            "token",
            token,
            "code",
            token,
            "label",
            label.trim(),
            "expiresInDays",
            30);
      }
    }
    throw MeetingService.error(HttpStatus.SERVICE_UNAVAILABLE, "초대코드를 다시 만들어 주세요.");
  }

  public List<Map<String, Object>> list(String id, String host) {
    meetingService.requireHost(id, host, false);
    return repository.findInvites(id);
  }

  public Map<String, Object> preview(String token) {
    var l = link(token, false);
    var m = meetingService.meeting((String) l.get("meetingId"), false);
    return Map.of("title", m.get("title"), "joined", l.get("userId") != null);
  }

  public Map<String, Object> reveal(String id, String host, String linkId) {
    meetingService.requireHost(id, host, false);
    var rows = repository.findInviteCode(linkId, id);
    if (rows.isEmpty()) throw MeetingService.error(HttpStatus.NOT_FOUND, "초대를 찾을 수 없습니다.");
    var row = rows.getFirst();
    if (((Number) row.get("revoked")).intValue() == 1)
      throw MeetingService.error(HttpStatus.CONFLICT, "취소한 초대는 다시 보낼 수 없습니다.");
    if (row.get("code_cipher") == null || Boolean.TRUE.equals(row.get("expired")))
      return Map.of("recoverable", false, "label", row.get("label"), "expired", row.get("expired"));
    String token = cipher.decrypt((String) row.get("code_cipher"), linkId);
    return Map.of("recoverable", true, "token", token, "code", token, "label", row.get("label"));
  }

  public Map<String, Object> reissue(String id, String host, String linkId) {
    var rows = repository.lockInvite(linkId, id);
    meetingService.requireHost(id, host, true);
    if (rows.isEmpty()) throw MeetingService.error(HttpStatus.NOT_FOUND, "초대를 찾을 수 없습니다.");
    var row = rows.getFirst();
    if (((Number) row.get("revoked")).intValue() == 1)
      throw MeetingService.error(HttpStatus.CONFLICT, "취소한 초대는 재발급할 수 없습니다.");
    for (int attempt = 0; attempt < 10; attempt++) {
      String token = candidateCode(), hash = hash(token);
      lockCode(token);
      if (repository.replaceInviteCode(
              hash, cipher.encrypt(token, linkId), OffsetDateTime.now().plusDays(30), linkId, hash)
          == 1)
        return Map.of(
            "recoverable", true, "token", token, "code", token, "label", row.get("label"));
    }
    throw MeetingService.error(HttpStatus.SERVICE_UNAVAILABLE, "초대코드를 다시 발급해 주세요.");
  }

  public AccountDtos.Account enter(String token, String name) {
    var l = link(token, true);
    String id = (String) l.get("meetingId");
    meetingService.meeting(id, true);
    if (l.get("userId") != null) return accountService.account(repository.findUsername(l.get("userId")));
    if (name == null || name.isBlank() || name.length() > 30)
      throw MeetingService.error(HttpStatus.BAD_REQUEST, "모임에서 사용할 이름을 1–30자로 입력해 주세요.");
    if (repository.countMembers(id) >= 100)
      throw MeetingService.error(HttpStatus.CONFLICT, "모임은 최대 100명까지 참여할 수 있습니다.");
    var guest = accountService.guest(name.strip());
    repository.insertMember(id, guest.id());
    repository.assignGuest(guest.id(), l.get("id"));
    return guest;
  }

  public void revoke(String id, String host, String linkId) {
    // Lock the link before the meeting, matching enter() to avoid lock inversion.
    var users = repository.lockInviteGuest(linkId, id);
    meetingService.requireHost(id, host, true);
    if (users.isEmpty()) throw MeetingService.error(HttpStatus.NOT_FOUND, "초대 링크를 찾을 수 없습니다.");
    String user = users.getFirst();
    if (user != null) {
      repository.deleteVotes(id, user);
      repository.deleteMember(id, user);
    }
    repository.revokeInvite(linkId);
  }
}
