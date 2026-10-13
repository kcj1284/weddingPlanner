package kr.cheongmo.guest;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GroupInviteRepository {
  private final JdbcTemplate jdbcTemplate;

  public GroupInviteRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Integer countActiveInvites(String meetingId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM group_invite WHERE meeting_id=? AND revoked=0 AND"
            + " expires_at>CURRENT_TIMESTAMP",
        Integer.class,
        meetingId);
  }

  public int insertInvite(
      String id, String meetingId, String tokenHash, java.time.OffsetDateTime expiresAt) {
    return jdbcTemplate.update(
        "INSERT INTO group_invite(id,meeting_id,token_hash,expires_at) VALUES(?,?,?,?)",
        id,
        meetingId,
        tokenHash,
        expiresAt);
  }

  public List<String> findMeetingByToken(boolean lock, String tokenHash) {
    return jdbcTemplate.query(
        "SELECT meeting_id FROM group_invite WHERE token_hash=? AND revoked=0 AND"
            + " expires_at>CURRENT_TIMESTAMP"
            + (lock ? " FOR UPDATE" : ""),
        (r, i) -> r.getString(1),
        tokenHash);
  }

  public Integer countMembership(String meetingId, String userId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM member WHERE meeting_id=? AND user_id=?",
        Integer.class,
        meetingId,
        userId);
  }

  public List<Map<String, Object>> findPersonalInvite(String meetingId, String guestUserId) {
    return jdbcTemplate.queryForList(
        "SELECT id,code_cipher FROM guest_link WHERE meeting_id=? AND guest_user_id=? AND revoked=0"
            + " AND expires_at>CURRENT_TIMESTAMP AND code_cipher IS NOT NULL ORDER BY created_at"
            + " DESC LIMIT 1",
        meetingId,
        guestUserId);
  }

  public Integer countMembers(String meetingId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM member WHERE meeting_id=?", Integer.class, meetingId);
  }

  public int insertMember(String meetingId, String userId) {
    return jdbcTemplate.update("INSERT INTO member(meeting_id,user_id) VALUES(?,?)", meetingId, userId);
  }

  public int insertPersonalInvite(
      String id,
      String meetingId,
      String label,
      String tokenHash,
      String guestUserId,
      java.time.OffsetDateTime expiresAt,
      String codeCipher) {
    return jdbcTemplate.update(
        "INSERT INTO"
            + " guest_link(id,meeting_id,label,token_hash,guest_user_id,expires_at,code_cipher)"
            + " VALUES(?,?,?,?,?,?,?) ON CONFLICT(token_hash) DO NOTHING",
        id,
        meetingId,
        label,
        tokenHash,
        guestUserId,
        expiresAt,
        codeCipher);
  }

  public List<Map<String, Object>> findInvites(String meetingId) {
    return jdbcTemplate.query(
        "SELECT id,revoked,expires_at,expires_at<=CURRENT_TIMESTAMP AS expired FROM group_invite"
            + " WHERE meeting_id=? ORDER BY created_at DESC",
        (r, i) ->
            Map.of(
                "id",
                r.getString(1),
                "revoked",
                r.getInt(2) == 1,
                "expired",
                r.getBoolean("expired"),
                "expiresAt",
                r.getTimestamp(3)
                    .toInstant()
                    .atZone(ZoneId.of("Asia/Seoul"))
                    .toLocalDateTime()
                    .toString()),
        meetingId);
  }

  public List<String> lockInvite(String id, String meetingId) {
    return jdbcTemplate.query(
        "SELECT id FROM group_invite WHERE id=? AND meeting_id=? FOR UPDATE",
        (r, i) -> r.getString(1),
        id,
        meetingId);
  }

  public int revokeInvite(String id) {
    return jdbcTemplate.update("UPDATE group_invite SET revoked=1 WHERE id=?", id);
  }
}
