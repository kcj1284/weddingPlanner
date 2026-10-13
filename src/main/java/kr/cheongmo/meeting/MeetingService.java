package kr.cheongmo.meeting;

import jakarta.validation.constraints.*;
import java.net.URI;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import kr.cheongmo.meeting.dto.MeetingDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class MeetingService {
  private final MeetingRepository repository;

  public MeetingService(MeetingRepository repository) {
    this.repository = repository;
  }

  public static ResponseStatusException error(HttpStatus code, String text) {
    return new ResponseStatusException(code, text);
  }

  public static String text(String value) {
    return value == null ? "" : value.trim();
  }

  public String create(String user, MeetingDtos.Create in) {
    if (repository.countGuestAccount(user) > 0)
      throw error(HttpStatus.FORBIDDEN, "모임을 열려면 호스트 계정으로 가입·로그인해 주세요.");
    if (text(in.title()).isEmpty()
        || in.title().length() > 80
        || text(in.area()).isEmpty()
        || in.area().length() > 80
        || in.expectedCount() < 2
        || in.expectedCount() > 100
        || text(in.description()).length() > 500)
      throw error(HttpStatus.BAD_REQUEST, "모임 이름·지역·예상 인원을 확인해 주세요.");
    String id = UUID.randomUUID().toString();
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    repository.insertMeeting(
        id,
        user,
        text(in.title()),
        text(in.description()),
        text(in.area()),
        in.expectedCount(),
        token);
    repository.insertMember(id, user);
    return id;
  }

  public List<MeetingDtos.Summary> list(String user) {
    return repository.findMeetingsForMember(user);
  }

  public Map<String, Object> meeting(String id, boolean lock) {
    return repository.findMeeting(lock, id).stream()
        .findFirst()
        .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "모임을 찾을 수 없습니다."));
  }

  public void requireMember(String id, String user) {
    if (repository.countMembership(id, user) == 0)
      throw error(HttpStatus.FORBIDDEN, "초대받은 모임에 먼저 참여해 주세요.");
  }

  public Map<String, Object> requireHost(String id, String user, boolean lock) {
    Map<String, Object> m = meeting(id, lock);
    if (!user.equals(m.get("host_id"))) throw error(HttpStatus.FORBIDDEN, "호스트만 사용할 수 있습니다.");
    return m;
  }

  public void open(Map<String, Object> m) {
    if (!"OPEN".equals(m.get("status")))
      throw error(HttpStatus.CONFLICT, "확정된 모임입니다. 호스트가 투표를 다시 열어야 합니다.");
  }

  public String join(String token, String user) {
    if (repository.countGuestAccount(user) > 0)
      throw error(HttpStatus.FORBIDDEN, "게스트는 받은 전용 링크로 참여해 주세요.");
    String id =
        repository.findMeetingByInviteToken(token).stream()
            .findFirst()
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "초대 링크를 확인해 주세요."));
    meeting(id, true);
    if (repository.countMembership(id, user) == 0) {
      if (repository.countMembers(id) >= 100)
        throw error(HttpStatus.CONFLICT, "모임은 최대 100명까지 참여할 수 있습니다.");
      repository.insertMember(id, user);
    }
    return id;
  }

  public MeetingDtos.Detail detail(String id, String user) {
    requireMember(id, user);
    Map<String, Object> meeting = meeting(id, false);
    boolean host = user.equals(meeting.get("host_id"));
    return new MeetingDtos.Detail(
        id,
        (String) meeting.get("title"),
        (String) meeting.get("description"),
        (String) meeting.get("area"),
        (String) meeting.get("status"),
        (Integer) meeting.get("expectedCount"),
        host ? "HOST" : "GUEST",
        host ? (String) meeting.get("invite_token") : null,
        (String) meeting.get("final_time_id"),
        (String) meeting.get("final_place_id"),
        repository.findDisplayName(meeting.get("host_id")),
        repository.countMembers(id),
        repository.countRespondents(id),
        repository.findVotes(id, user),
        repository.findOptions(id));
  }

  public String addOption(String id, String user, MeetingDtos.OptionInput in) {
    open(requireHost(id, user, true));
    if (!List.of("TIME", "PLACE").contains(in.kind())
        || text(in.title()).isEmpty()
        || in.title().length() > 120
        || text(in.detail()).length() > 300
        || text(in.phone()).length() > 40
        || text(in.mapUrl()).length() > 500) throw error(HttpStatus.BAD_REQUEST, "후보 정보를 확인해 주세요.");
    if ("TIME".equals(in.kind())) {
      try {
        LocalDateTime.parse(in.title());
      } catch (DateTimeParseException e) {
        throw error(HttpStatus.BAD_REQUEST, "날짜와 시간을 확인해 주세요.");
      }
    }
    if (!text(in.mapUrl()).isEmpty()) {
      try {
        URI url = URI.create(in.mapUrl());
        if (!List.of("http", "https").contains(url.getScheme())
            || url.getHost() == null
            || url.getUserInfo() != null) throw new IllegalArgumentException();
      } catch (IllegalArgumentException e) {
        throw error(HttpStatus.BAD_REQUEST, "지도 링크는 올바른 https 또는 http 주소여야 합니다.");
      }
    }
    if (repository.countOptionsByKind(id, in.kind()) >= 30)
      throw error(HttpStatus.CONFLICT, "종류별 후보는 최대 30개입니다.");
    if (repository.countDuplicateOption(id, in.kind(), text(in.title()), text(in.detail())) > 0)
      throw error(HttpStatus.CONFLICT, "같은 후보가 이미 있습니다.");
    String option = UUID.randomUUID().toString();
    repository.insertOption(
        option,
        id,
        in.kind(),
        text(in.title()),
        text(in.detail()),
        text(in.mapUrl()),
        text(in.phone()));
    return option;
  }

  public void vote(String id, String user, List<String> choices) {
    requireMember(id, user);
    open(meeting(id, true));
    if (choices == null || choices.size() > 60 || new HashSet<>(choices).size() != choices.size())
      throw error(HttpStatus.BAD_REQUEST, "중복되거나 너무 많은 선택입니다.");
    for (String choice : choices)
      if (repository.countMeetingOption(choice, id) == 0)
        throw error(HttpStatus.BAD_REQUEST, "이 모임의 후보만 선택할 수 있습니다.");
    repository.deleteVotes(id, user);
    for (String choice : choices) repository.insertVote(id, user, choice);
  }

  public void confirm(String id, String user, String time, String place) {
    open(requireHost(id, user, true));
    if (repository.countTimeOption(time, id) != 1 || repository.countPlaceOption(place, id) != 1)
      throw error(HttpStatus.BAD_REQUEST, "이 모임의 시간 하나와 장소 하나를 선택해 주세요.");
    repository.confirmMeeting(time, place, id);
  }

  public void reopen(String id, String user) {
    requireHost(id, user, true);
    repository.reopenMeeting(id);
  }

  public void reservation(String id, String user, String option, String status, String note) {
    requireHost(id, user, true);
    if (!List.of("UNKNOWN", "AVAILABLE", "BOOKED").contains(status) || text(note).length() > 300)
      throw error(HttpStatus.BAD_REQUEST, "예약 확인 상태와 메모를 확인해 주세요.");
    if (repository.updateReservation(status, text(note), option, id) != 1)
      throw error(HttpStatus.NOT_FOUND, "장소 후보를 찾을 수 없습니다.");
  }
}
