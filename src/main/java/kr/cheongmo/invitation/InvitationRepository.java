package kr.cheongmo.invitation;

import java.time.*;
import java.util.*;
import kr.cheongmo.invitation.dto.InvitationDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InvitationRepository {
  private final JdbcTemplate jdbcTemplate;
  private final tools.jackson.databind.ObjectMapper objectMapper;

  public InvitationRepository(JdbcTemplate jdbcTemplate, tools.jackson.databind.ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public Integer countGuestAccount(String userId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM account_kind WHERE user_id=? AND kind='GUEST'",
        Integer.class,
        userId);
  }

  public List<InvitationDtos.Summary> findInvitationSummaries(String ownerId) {
    return jdbcTemplate.query(
        "SELECT id,payload,published,version FROM invitation WHERE owner_id=? ORDER BY updated_at"
            + " DESC",
        (rs, n) -> {
          InvitationDtos.Body body =
              objectMapper.readValue(rs.getString("payload"), InvitationDtos.Body.class);
          return new InvitationDtos.Summary(
              rs.getString("id"), body.groom(), body.bride(), rs.getInt("published") == 1);
        },
        ownerId);
  }

  public List<InvitationDtos.Card> findOwnedInvitation(String id, String ownerId) {
    return jdbcTemplate.query(
        "SELECT * FROM invitation WHERE id=? AND owner_id=?", (rs, n) -> card(rs), id, ownerId);
  }

  public List<InvitationDtos.Body> findPublishedBody(String shareToken) {
    return jdbcTemplate.query(
        "SELECT payload FROM invitation WHERE share_token=? AND published=1",
        (rs, n) -> objectMapper.readValue(rs.getString(1), InvitationDtos.Body.class),
        shareToken);
  }

  public Integer countOwnedInvitations(String ownerId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM invitation WHERE owner_id=?", Integer.class, ownerId);
  }

  public int insertInvitation(String id, String ownerId, String shareToken, String payload) {
    return jdbcTemplate.update(
        "INSERT INTO invitation(id,owner_id,share_token,payload) VALUES(?,?,?,?)",
        id,
        ownerId,
        shareToken,
        payload);
  }

  public int updateInvitation(String payload, String id, String ownerId, int version) {
    return jdbcTemplate.update(
        "UPDATE invitation SET payload=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?"
            + " AND owner_id=? AND version=?",
        payload,
        id,
        ownerId,
        version);
  }

  public int updatePublication(int published, String id, String ownerId, int version) {
    return jdbcTemplate.update(
        "UPDATE invitation SET published=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE"
            + " id=? AND owner_id=? AND version=?",
        published,
        id,
        ownerId,
        version);
  }

  private InvitationDtos.Card card(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new InvitationDtos.Card(
        rs.getString("id"),
        rs.getString("share_token"),
        rs.getInt("published") == 1,
        rs.getInt("version"),
        objectMapper.readValue(rs.getString("payload"), InvitationDtos.Body.class));
  }
}
