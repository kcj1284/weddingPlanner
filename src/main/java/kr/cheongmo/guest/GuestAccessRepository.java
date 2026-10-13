package kr.cheongmo.guest;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GuestAccessRepository {
  private final JdbcTemplate jdbcTemplate;

  public GuestAccessRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void lockInviteCode(String tokenHash) {
    jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> {}, tokenHash);
  }

  public List<Map<String, Object>> findUsableLink(boolean lock, String tokenHash) {
    return jdbcTemplate.query(
        "SELECT * FROM guest_link WHERE token_hash=? AND revoked=0 AND expires_at>CURRENT_TIMESTAMP"
            + (lock ? " FOR UPDATE" : ""),
        (rs, n) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("id", rs.getString("id"));
          m.put("meetingId", rs.getString("meeting_id"));
          m.put("userId", rs.getString("guest_user_id"));
          return m;
        },
        tokenHash);
  }

  public Integer countActiveLinks(String meetingId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM guest_link WHERE meeting_id=? AND revoked=0 AND"
            + " expires_at>CURRENT_TIMESTAMP",
        Integer.class,
        meetingId);
  }

  public int insertLink(
      String id,
      String meetingId,
      String label,
      String tokenHash,
      java.time.OffsetDateTime expiresAt,
      String codeCipher) {
    return jdbcTemplate.update(
        "INSERT INTO guest_link(id,meeting_id,label,token_hash,expires_at,code_cipher)"
            + " VALUES(?,?,?,?,?,?) ON CONFLICT (token_hash) DO NOTHING",
        id,
        meetingId,
        label,
        tokenHash,
        expiresAt,
        codeCipher);
  }

  public List<Map<String, Object>> findInvites(String meetingId) {
    return jdbcTemplate.query(
        "SELECT l.*,l.expires_at<=CURRENT_TIMESTAMP AS expired,u.display_name FROM guest_link l"
            + " LEFT JOIN app_user u ON u.id=l.guest_user_id WHERE l.meeting_id=? ORDER BY"
            + " l.created_at",
        (rs, n) -> {
          Map<String, Object> out = new LinkedHashMap<>();
          out.put("id", rs.getString("id"));
          out.put("label", rs.getString("label"));
          out.put("displayName", rs.getString("display_name"));
          out.put("revoked", rs.getInt("revoked") == 1);
          out.put("expired", rs.getBoolean("expired"));
          out.put(
              "expiresAt",
              rs.getTimestamp("expires_at")
                  .toInstant()
                  .atZone(ZoneId.of("Asia/Seoul"))
                  .toLocalDateTime()
                  .toString());
          return out;
        },
        meetingId);
  }

  public List<Map<String, Object>> findInviteCode(String id, String meetingId) {
    return jdbcTemplate.queryForList(
        "SELECT label,revoked,code_cipher,expires_at<=CURRENT_TIMESTAMP AS expired FROM guest_link"
            + " WHERE id=? AND meeting_id=?",
        id,
        meetingId);
  }

  public List<Map<String, Object>> lockInvite(String id, String meetingId) {
    return jdbcTemplate.queryForList(
        "SELECT label,revoked FROM guest_link WHERE id=? AND meeting_id=? FOR UPDATE",
        id,
        meetingId);
  }

  public int replaceInviteCode(
      String tokenHash,
      String codeCipher,
      java.time.OffsetDateTime expiresAt,
      String id,
      String tokenHash2) {
    return jdbcTemplate.update(
        "UPDATE guest_link SET token_hash=?,code_cipher=?,expires_at=? WHERE id=? AND NOT"
            + " EXISTS(SELECT 1 FROM guest_link WHERE token_hash=?)",
        tokenHash,
        codeCipher,
        expiresAt,
        id,
        tokenHash2);
  }

  public String findUsername(Object id) {
    return jdbcTemplate.queryForObject("SELECT username FROM app_user WHERE id=?", String.class, id);
  }

  public Integer countMembers(String meetingId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM member WHERE meeting_id=?", Integer.class, meetingId);
  }

  public int insertMember(String meetingId, String userId) {
    return jdbcTemplate.update("INSERT INTO member(meeting_id,user_id) VALUES(?,?)", meetingId, userId);
  }

  public int assignGuest(String guestUserId, Object id) {
    return jdbcTemplate.update("UPDATE guest_link SET guest_user_id=? WHERE id=?", guestUserId, id);
  }

  public List<String> lockInviteGuest(String id, String meetingId) {
    return jdbcTemplate.query(
        "SELECT guest_user_id FROM guest_link WHERE id=? AND meeting_id=? FOR UPDATE",
        (rs, n) -> rs.getString(1),
        id,
        meetingId);
  }

  public int deleteVotes(String meetingId, String userId) {
    return jdbcTemplate.update("DELETE FROM vote WHERE meeting_id=? AND user_id=?", meetingId, userId);
  }

  public int deleteMember(String meetingId, String userId) {
    return jdbcTemplate.update("DELETE FROM member WHERE meeting_id=? AND user_id=?", meetingId, userId);
  }

  public int revokeInvite(String id) {
    return jdbcTemplate.update("UPDATE guest_link SET revoked=1 WHERE id=?", id);
  }
}
