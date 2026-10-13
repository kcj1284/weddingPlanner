package kr.cheongmo.account;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRecoveryRepository {
  private final JdbcTemplate jdbcTemplate;

  public AccountRecoveryRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Integer countEligibleAccount(String id) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM app_user u WHERE u.id=? AND NOT EXISTS(SELECT 1 FROM account_kind k"
            + " WHERE k.user_id=u.id AND k.kind='GUEST') AND NOT EXISTS(SELECT 1 FROM"
            + " social_identity s WHERE s.user_id=u.id)",
        Integer.class,
        id);
  }

  public List<Map<String, Object>> findContact(String userId) {
    return jdbcTemplate.queryForList("SELECT email FROM recovery_contact WHERE user_id=?", userId);
  }

  public Integer incrementRequestAttempts(String bucket) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO recovery_request_limit(bucket,attempts,expires_at)"
            + " VALUES(?,1,CURRENT_TIMESTAMP+INTERVAL '15 minutes') ON CONFLICT(bucket) DO UPDATE"
            + " SET attempts=CASE WHEN recovery_request_limit.expires_at<CURRENT_TIMESTAMP THEN 1"
            + " ELSE recovery_request_limit.attempts+1 END,expires_at=CASE WHEN"
            + " recovery_request_limit.expires_at<CURRENT_TIMESTAMP THEN CURRENT_TIMESTAMP+INTERVAL"
            + " '15 minutes' ELSE recovery_request_limit.expires_at END RETURNING attempts",
        Integer.class,
        bucket);
  }

  public int invalidatePurposeTokens(String userId, String purpose) {
    return jdbcTemplate.update(
        "UPDATE recovery_token SET used_at=CURRENT_TIMESTAMP WHERE user_id=? AND purpose=? AND"
            + " used_at IS NULL",
        userId,
        purpose);
  }

  public int insertToken(
      String tokenHash,
      String userId,
      String purpose,
      String email,
      java.time.OffsetDateTime expiresAt) {
    return jdbcTemplate.update(
        "INSERT INTO recovery_token(token_hash,user_id,purpose,email,expires_at) VALUES(?,?,?,?,?)",
        tokenHash,
        userId,
        purpose,
        email,
        expiresAt);
  }

  public String lockAccount(String id) {
    return jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE", String.class, id);
  }

  public String findPasswordHash(String id) {
    return jdbcTemplate.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, id);
  }

  public List<Map<String, Object>> findContactOwner(String email) {
    return jdbcTemplate.queryForList("SELECT user_id FROM recovery_contact WHERE email=?", email);
  }

  public List<Map<String, Object>> findUsernameByContact(String email) {
    return jdbcTemplate.queryForList(
        "SELECT u.username,u.id FROM recovery_contact c JOIN app_user u ON u.id=c.user_id WHERE"
            + " c.email=?",
        email);
  }

  public List<Map<String, Object>> findTokenOwner(String tokenHash) {
    return jdbcTemplate.queryForList("SELECT user_id FROM recovery_token WHERE token_hash=?", tokenHash);
  }

  public String lockAccount(Object id) {
    return jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE", String.class, id);
  }

  public List<Map<String, Object>> findUsableToken(String tokenHash, String purpose) {
    return jdbcTemplate.queryForList(
        "SELECT * FROM recovery_token WHERE token_hash=? AND purpose=? AND used_at IS NULL AND"
            + " expires_at>CURRENT_TIMESTAMP FOR UPDATE",
        tokenHash,
        purpose);
  }

  public int saveContact(String userId, String email) {
    return jdbcTemplate.update(
        "INSERT INTO recovery_contact(user_id,email) VALUES(?,?) ON CONFLICT(user_id) DO UPDATE SET"
            + " email=EXCLUDED.email,verified_at=CURRENT_TIMESTAMP",
        userId,
        email);
  }

  public int invalidateAllTokens(String userId) {
    return jdbcTemplate.update(
        "UPDATE recovery_token SET used_at=CURRENT_TIMESTAMP WHERE user_id=? AND used_at IS NULL",
        userId);
  }

  public Integer countMatchingContact(String userId, Object email) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM recovery_contact WHERE user_id=? AND email=?",
        Integer.class,
        userId,
        email);
  }

  public int updatePasswordAndVersion(String passwordHash, String id) {
    return jdbcTemplate.update(
        "UPDATE app_user SET password_hash=?,auth_version=auth_version+1 WHERE id=?",
        passwordHash,
        id);
  }
}
