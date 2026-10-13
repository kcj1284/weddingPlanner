package kr.cheongmo.guest;

import jakarta.servlet.http.*;
import jakarta.validation.constraints.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import kr.cheongmo.account.AccountService;
import kr.cheongmo.account.dto.AccountDtos;
import kr.cheongmo.guest.dto.GroupInviteDtos;
import kr.cheongmo.meeting.MeetingService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Service
@Transactional
public class GroupInviteService {
  private final GroupInviteRepository repository;
  private final MeetingService meetingService;
  private final AccountService accountService;
  private final InviteCodeCipher cipher;
  private final GuestAccessService guests;

  public GroupInviteService(
      GroupInviteRepository repository,
      MeetingService meetingService,
      AccountService accountService,
      InviteCodeCipher cipher,
      GuestAccessService guests) {
    this.repository = repository;
    this.meetingService = meetingService;
    this.accountService = accountService;
    this.cipher = cipher;
    this.guests = guests;
  }

  public String token() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public Map<String, Object> create(String id, String host) {
    meetingService.requireHost(id, host, true);
    if (repository.countActiveInvites(id) >= 20)
      throw MeetingService.error(HttpStatus.CONFLICT, "사용 가능한 단체 초대 링크는 최대 20개입니다.");
    String token = token(), invite = UUID.randomUUID().toString();
    repository.insertInvite(
        invite, id, GuestAccessService.hash(token), OffsetDateTime.now().plusDays(30));
    return Map.of("id", invite, "token", token, "expiresInDays", 30);
  }

  public String meetingId(String token, boolean lock) {
    return repository.findMeetingByToken(lock, GuestAccessService.hash(token)).stream()
        .findFirst()
        .orElseThrow(() -> MeetingService.error(HttpStatus.NOT_FOUND, "단체 초대 링크가 만료되었거나 취소되었습니다."));
  }

  public Map<String, Object> preview(String token) {
    return preview(token, null);
  }

  public Map<String, Object> preview(String token, AccountDtos.Account current) {
    String id = meetingId(token, false);
    boolean joined =
        current != null && current.guest() && repository.countMembership(id, current.id()) > 0;
    return Map.of(
        "title", meetingService.meeting(id, false).get("title"), "group", true, "joined", joined);
  }

  public GroupInviteDtos.Joined enter(String token, String name, AccountDtos.Account current) {
    String id = meetingId(token, true);
    meetingService.meeting(id, true);
    if (current != null && current.guest() && repository.countMembership(id, current.id()) > 0) {
      var personal = repository.findPersonalInvite(id, current.id());
      if (!personal.isEmpty())
        return new GroupInviteDtos.Joined(
            current,
            id,
            cipher.decrypt(
                (String) personal.getFirst().get("code_cipher"),
                (String) personal.getFirst().get("id")));
      return issuePersonal(id, current, current.displayName());
    }
    if (name == null || name.isBlank() || name.length() > 30)
      throw MeetingService.error(HttpStatus.BAD_REQUEST, "모임에서 사용할 이름을 1–30자로 입력해 주세요.");
    if (repository.countMembers(id) >= 100)
      throw MeetingService.error(HttpStatus.CONFLICT, "모임은 최대 100명까지 참여할 수 있습니다.");
    var guest = accountService.guest(name.strip());
    repository.insertMember(id, guest.id());
    return issuePersonal(id, guest, name.strip());
  }

  private GroupInviteDtos.Joined issuePersonal(String id, AccountDtos.Account guest, String name) {
    String personalId = UUID.randomUUID().toString();
    for (int attempt = 0; attempt < 10; attempt++) {
      String personal = guests.candidateCode();
      guests.lockCode(personal);
      if (repository.insertPersonalInvite(
              personalId,
              id,
              name,
              GuestAccessService.hash(personal),
              guest.id(),
              OffsetDateTime.now().plusDays(30),
              cipher.encrypt(personal, personalId))
          == 1) return new GroupInviteDtos.Joined(guest, id, personal);
    }
    throw MeetingService.error(HttpStatus.SERVICE_UNAVAILABLE, "초대코드를 다시 발급해 주세요.");
  }

  public List<Map<String, Object>> list(String id, String host) {
    meetingService.requireHost(id, host, false);
    return repository.findInvites(id);
  }

  public void revoke(String id, String host, String invite) {
    // Keep lock order identical to join: invitation, then meeting.
    var rows = repository.lockInvite(invite, id);
    meetingService.requireHost(id, host, true);
    if (rows.isEmpty()) throw MeetingService.error(HttpStatus.NOT_FOUND, "단체 초대를 찾을 수 없습니다.");
    repository.revokeInvite(invite);
  }
}
