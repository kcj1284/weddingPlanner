package kr.cheongmo.place;

import java.time.*;
import java.util.*;
import kr.cheongmo.place.dto.PlaceDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlaceDiscoveryRepository {
  private final JdbcTemplate jdbcTemplate;
  private final tools.jackson.databind.ObjectMapper objectMapper;

  public PlaceDiscoveryRepository(JdbcTemplate jdbcTemplate, tools.jackson.databind.ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public List<PlaceDtos.Cached> findCache(String cacheKey) {
    return jdbcTemplate.query(
        "SELECT payload,expires_at>CURRENT_TIMESTAMP FROM place_discovery_cache WHERE cache_key=?",
        (r, i) -> new PlaceDtos.Cached(objectMapper.readValue(r.getString(1), Map.class), r.getBoolean(2)),
        cacheKey);
  }

  public int touchCache(String cacheKey) {
    return jdbcTemplate.update(
        "UPDATE place_discovery_cache SET accessed_at=CURRENT_TIMESTAMP WHERE cache_key=?",
        cacheKey);
  }

  public int saveCache(
      String cacheKey,
      String kind,
      String queryText,
      String placeType,
      String payload,
      java.time.OffsetDateTime expiresAt) {
    return jdbcTemplate.update(
        "INSERT INTO place_discovery_cache(cache_key,kind,query_text,place_type,payload,expires_at)"
            + " VALUES(?,?,?,?,?,?) ON CONFLICT(cache_key) DO UPDATE SET"
            + " payload=excluded.payload,expires_at=excluded.expires_at",
        cacheKey,
        kind,
        queryText,
        placeType,
        payload,
        expiresAt);
  }

  public int deleteUnusedCache() {
    return jdbcTemplate.update(
        "DELETE FROM place_discovery_cache WHERE accessed_at<CURRENT_TIMESTAMP-INTERVAL '30 days'");
  }

  public List<Map<String, Object>> findExpiredActiveCache() {
    return jdbcTemplate.queryForList(
        "SELECT kind,query_text,place_type FROM place_discovery_cache WHERE"
            + " expires_at<CURRENT_TIMESTAMP AND accessed_at>CURRENT_TIMESTAMP-INTERVAL '7 days'"
            + " ORDER BY expires_at LIMIT 20");
  }
}
