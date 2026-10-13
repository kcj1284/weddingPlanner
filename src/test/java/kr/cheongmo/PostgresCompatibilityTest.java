package kr.cheongmo;

import static org.assertj.core.api.Assertions.*;

import kr.cheongmo.meeting.dto.MeetingDtos;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PostgresCompatibilityTest extends MeetingIntegrationTest {
  @Test
  void duplicatePlaceTreatsNullAndEmptyDetailsConsistently() {
    String option =
        service.addOption(meeting, host, new MeetingDtos.OptionInput("PLACE", "카페", "", "", ""));
    assertThatThrownBy(
            () ->
                service.addOption(
                    meeting, host, new MeetingDtos.OptionInput("PLACE", "카페", "", "", "")))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    db.update("UPDATE poll_option SET detail=NULL WHERE id=?", option);
    assertThatThrownBy(
            () ->
                service.addOption(
                    meeting, host, new MeetingDtos.OptionInput("PLACE", "카페", "", "", "")))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
  }

  @Test
  void postgresAndVersionedSchemaAreUsedForIntegrationTests() {
    assertThat(db.queryForObject("SELECT version()", String.class)).startsWith("PostgreSQL");
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success=true", Integer.class))
        .isGreaterThanOrEqualTo(1);
    assertThat(
            db.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_name='guest_link' AND"
                    + " column_name='expires_at'",
                String.class))
        .isEqualTo("timestamp with time zone");
  }
}
