package kr.cheongmo.meeting;

import java.time.*;
import java.util.*;
import kr.cheongmo.meeting.dto.MeetingDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MeetingRepository {
  private final JdbcTemplate jdbcTemplate;

  public MeetingRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Integer countGuestAccount(String userId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM account_kind WHERE user_id=? AND kind='GUEST'",
        Integer.class,
        userId);
  }

  public int insertMeeting(
      String id,
      String hostId,
      String title,
      String description,
      String area,
      int expectedCount,
      String inviteToken) {
    return jdbcTemplate.update(
        "INSERT INTO meeting(id,host_id,title,description,area,expected_count,invite_token)"
            + " VALUES(?,?,?,?,?,?,?)",
        id,
        hostId,
        title,
        description,
        area,
        expectedCount,
        inviteToken);
  }

  public int insertMember(String meetingId, String userId) {
    return jdbcTemplate.update("INSERT INTO member(meeting_id,user_id) VALUES(?,?)", meetingId, userId);
  }

  public List<MeetingDtos.Summary> findMeetingsForMember(String userId) {
    return jdbcTemplate.query(
        "SELECT m.*, (SELECT COUNT(*) FROM member x WHERE x.meeting_id=m.id) member_count FROM"
            + " meeting m JOIN member mem ON mem.meeting_id=m.id WHERE mem.user_id=? ORDER BY"
            + " m.created_at DESC",
        (rs, n) ->
            new MeetingDtos.Summary(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("area"),
                rs.getString("status"),
                userId.equals(rs.getString("host_id")) ? "HOST" : "GUEST",
                rs.getInt("member_count")),
        userId);
  }

  public List<Map<String, Object>> findMeeting(boolean lock, String id) {
    return jdbcTemplate.query(
        "SELECT * FROM meeting WHERE id=?" + (lock ? " FOR UPDATE" : ""),
        (rs, n) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          for (String col :
              List.of(
                  "id",
                  "host_id",
                  "title",
                  "description",
                  "area",
                  "invite_token",
                  "status",
                  "final_time_id",
                  "final_place_id")) m.put(col, rs.getString(col));
          m.put("expectedCount", rs.getInt("expected_count"));
          return m;
        },
        id);
  }

  public Integer countMembership(String meetingId, String userId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM member WHERE meeting_id=? AND user_id=?",
        Integer.class,
        meetingId,
        userId);
  }

  public List<String> findMeetingByInviteToken(String inviteToken) {
    return jdbcTemplate.query(
        "SELECT id FROM meeting WHERE invite_token=?", (rs, n) -> rs.getString(1), inviteToken);
  }

  public Integer countMembers(String meetingId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM member WHERE meeting_id=?", Integer.class, meetingId);
  }

  public String findDisplayName(Object id) {
    return jdbcTemplate.queryForObject("SELECT display_name FROM app_user WHERE id=?", String.class, id);
  }

  public Integer countRespondents(String meetingId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(DISTINCT user_id) FROM vote WHERE meeting_id=?", Integer.class, meetingId);
  }

  public List<String> findVotes(String meetingId, String userId) {
    return jdbcTemplate.query(
        "SELECT option_id FROM vote WHERE meeting_id=? AND user_id=?",
        (rs, n) -> rs.getString(1),
        meetingId,
        userId);
  }

  public List<MeetingDtos.Option> findOptions(String meetingId) {
    return jdbcTemplate.query(
        "SELECT o.*, (SELECT COUNT(*) FROM vote v WHERE v.option_id=o.id) vote_count FROM"
            + " poll_option o WHERE o.meeting_id=? ORDER BY o.created_at,o.id",
        (rs, n) -> {
          return new MeetingDtos.Option(
              rs.getString("id"),
              rs.getString("kind"),
              rs.getString("title"),
              rs.getString("detail"),
              rs.getString("map_url"),
              rs.getString("phone"),
              rs.getString("reservation"),
              rs.getString("reservation_note"),
              rs.getInt("vote_count"));
        },
        meetingId);
  }

  public Integer countOptionsByKind(String meetingId, String kind) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM poll_option WHERE meeting_id=? AND kind=?",
        Integer.class,
        meetingId,
        kind);
  }

  public Integer countDuplicateOption(String meetingId, String kind, String title, String value4) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM poll_option WHERE meeting_id=? AND kind=? AND title=? AND"
            + " COALESCE(detail,'')=?",
        Integer.class,
        meetingId,
        kind,
        title,
        value4);
  }

  public int insertOption(
      String id,
      String meetingId,
      String kind,
      String title,
      String detail,
      String mapUrl,
      String phone) {
    return jdbcTemplate.update(
        "INSERT INTO poll_option(id,meeting_id,kind,title,detail,map_url,phone)"
            + " VALUES(?,?,?,?,?,?,?)",
        id,
        meetingId,
        kind,
        title,
        detail,
        mapUrl,
        phone);
  }

  public Integer countMeetingOption(String id, String meetingId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM poll_option WHERE id=? AND meeting_id=?",
        Integer.class,
        id,
        meetingId);
  }

  public int deleteVotes(String meetingId, String userId) {
    return jdbcTemplate.update("DELETE FROM vote WHERE meeting_id=? AND user_id=?", meetingId, userId);
  }

  public int insertVote(String meetingId, String userId, String optionId) {
    return jdbcTemplate.update(
        "INSERT INTO vote(meeting_id,user_id,option_id) VALUES(?,?,?)",
        meetingId,
        userId,
        optionId);
  }

  public Integer countTimeOption(String id, String meetingId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM poll_option WHERE id=? AND meeting_id=? AND kind='TIME'",
        Integer.class,
        id,
        meetingId);
  }

  public Integer countPlaceOption(String id, String meetingId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM poll_option WHERE id=? AND meeting_id=? AND kind='PLACE'",
        Integer.class,
        id,
        meetingId);
  }

  public int confirmMeeting(String finalTimeId, String finalPlaceId, String id) {
    return jdbcTemplate.update(
        "UPDATE meeting SET status='CONFIRMED',final_time_id=?,final_place_id=? WHERE id=?",
        finalTimeId,
        finalPlaceId,
        id);
  }

  public int reopenMeeting(String id) {
    return jdbcTemplate.update(
        "UPDATE meeting SET status='OPEN',final_time_id=NULL,final_place_id=NULL WHERE id=?", id);
  }

  public int updateReservation(
      String reservation, String reservationNote, String id, String meetingId) {
    return jdbcTemplate.update(
        "UPDATE poll_option SET reservation=?,reservation_note=? WHERE id=? AND meeting_id=? AND"
            + " kind='PLACE'",
        reservation,
        reservationNote,
        id,
        meetingId);
  }
}
