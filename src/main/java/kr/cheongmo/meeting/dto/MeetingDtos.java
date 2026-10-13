package kr.cheongmo.meeting.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public final class MeetingDtos {
  private MeetingDtos() {}

  public record Create(
      @NotBlank @Size(max = 80) String title,
      @Size(max = 500) String description,
      @NotBlank @Size(max = 80) String area,
      @Min(2) @Max(100) int expectedCount) {}

  public record OptionInput(
      @NotNull @Pattern(regexp = "TIME|PLACE") String kind,
      @NotBlank @Size(max = 120) String title,
      @Size(max = 300) String detail,
      @Size(max = 500) String mapUrl,
      @Size(max = 40) String phone) {}

  public record VoteInput(@NotNull @Size(max = 60) List<@NotBlank String> optionIds) {}

  public record Confirmation(@NotBlank String timeId, @NotBlank String placeId) {}

  public record Reservation(
      @NotNull @Pattern(regexp = "UNKNOWN|AVAILABLE|BOOKED") String status,
      @Size(max = 300) String note) {}

  public record Summary(
      String id, String title, String area, String status, String role, int memberCount) {}

  public record Option(
      String id,
      String kind,
      String title,
      String detail,
      String mapUrl,
      String phone,
      String reservation,
      String reservationNote,
      int votes) {}

  public record Detail(
      String id,
      String title,
      String description,
      String area,
      String status,
      int expectedCount,
      String role,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          String inviteToken,
      String finalTimeId,
      String finalPlaceId,
      String hostName,
      int memberCount,
      int responseCount,
      List<String> myVotes,
      List<Option> options) {}

  public record Created(String id) {}

  public record Saved(boolean saved) {}
}
