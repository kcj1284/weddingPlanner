package kr.cheongmo.account;

import java.time.*;
import java.util.*;
import kr.cheongmo.account.dto.AccountDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
  private final JdbcTemplate jdbcTemplate;

  public AccountRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public int insertAccount(String id, String username, String displayName, String passwordHash) {
    return jdbcTemplate.update(
        "INSERT INTO app_user(id,username,display_name,password_hash) VALUES(?,?,?,?)",
        id,
        username,
        displayName,
        passwordHash);
  }

  public List<AccountDtos.Account> findAccount(String username) {
    return jdbcTemplate.query(
        "SELECT u.id,u.username,u.display_name,CASE WHEN k.kind='GUEST' THEN 1 ELSE 0 END is_guest"
            + " FROM app_user u LEFT JOIN account_kind k ON k.user_id=u.id WHERE username=?",
        (rs, n) ->
            new AccountDtos.Account(
                rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4) == 1),
        username);
  }

  public Integer findAuthVersion(String username) {
    return jdbcTemplate.queryForObject(
        "SELECT auth_version FROM app_user WHERE username=?", Integer.class, username);
  }

  public List<org.springframework.security.core.userdetails.UserDetails> findLoginUser(
      String username) {
    return jdbcTemplate.query(
        "SELECT username,password_hash FROM app_user u WHERE username=? AND NOT EXISTS(SELECT 1"
            + " FROM account_kind k WHERE k.user_id=u.id AND k.kind='GUEST')",
        (rs, n) ->
            User.withUsername(rs.getString(1)).password(rs.getString(2)).roles("USER").build(),
        username);
  }

  public int insertGuestKind(String userId) {
    return jdbcTemplate.update("INSERT INTO account_kind(user_id,kind) VALUES(?,'GUEST')", userId);
  }

  public List<String> findSocialUsername(String provider, String subject) {
    return jdbcTemplate.query(
        "SELECT u.username FROM app_user u JOIN social_identity s ON s.user_id=u.id WHERE"
            + " s.provider=? AND s.subject=?",
        (rs, n) -> rs.getString(1),
        provider,
        subject);
  }

  public int insertSocialIdentity(String provider, String subject, String userId) {
    return jdbcTemplate.update(
        "INSERT INTO social_identity(provider,subject,user_id) VALUES(?,?,?)",
        provider,
        subject,
        userId);
  }
}
