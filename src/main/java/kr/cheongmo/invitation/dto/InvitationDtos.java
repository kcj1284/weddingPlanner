package kr.cheongmo.invitation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

public final class InvitationDtos {
  private InvitationDtos() {}

  public record Body(
      @NotNull @Pattern(regexp = "CLASSIC|GARDEN|PHOTO|NEWSPAPER") String template,
      @NotBlank @Size(max = 30) String groom,
      @NotBlank @Size(max = 30) String bride,
      @Size(max = 120) String groomParents,
      @Size(max = 120) String brideParents,
      @NotBlank @Size(max = 1500) String greeting,
      @NotBlank @Size(max = 25) String dateTime,
      @NotBlank @Size(max = 120) String venue,
      @NotBlank @Size(max = 200) String address,
      @Size(max = 1000) String transport,
      @Pattern(regexp = "[0-9+ -]{0,25}") String groomPhone,
      @Pattern(regexp = "[0-9+ -]{0,25}") String bridePhone,
      @Size(max = 750000) String cover,
      @NotNull @Size(max = 6) List<@NotNull @Size(max = 750000) String> gallery,
      @Valid Design design,
      @Valid GiftAccounts giftAccounts,
      @Valid Newspaper newspaper) {
    public Body(
        String template,
        String groom,
        String bride,
        String groomParents,
        String brideParents,
        String greeting,
        String dateTime,
        String venue,
        String address,
        String transport,
        String groomPhone,
        String bridePhone,
        String cover,
        List<String> gallery,
        Design design,
        GiftAccounts giftAccounts) {
      this(
          template,
          groom,
          bride,
          groomParents,
          brideParents,
          greeting,
          dateTime,
          venue,
          address,
          transport,
          groomPhone,
          bridePhone,
          cover,
          gallery,
          design,
          giftAccounts,
          null);
    }

    public Body(
        String template,
        String groom,
        String bride,
        String groomParents,
        String brideParents,
        String greeting,
        String dateTime,
        String venue,
        String address,
        String transport,
        String groomPhone,
        String bridePhone,
        String cover,
        List<String> gallery,
        Design design) {
      this(
          template,
          groom,
          bride,
          groomParents,
          brideParents,
          greeting,
          dateTime,
          venue,
          address,
          transport,
          groomPhone,
          bridePhone,
          cover,
          gallery,
          design,
          null);
    }

    public Body(
        String template,
        String groom,
        String bride,
        String groomParents,
        String brideParents,
        String greeting,
        String dateTime,
        String venue,
        String address,
        String transport,
        String groomPhone,
        String bridePhone,
        String cover,
        List<String> gallery) {
      this(
          template,
          groom,
          bride,
          groomParents,
          brideParents,
          greeting,
          dateTime,
          venue,
          address,
          transport,
          groomPhone,
          bridePhone,
          cover,
          gallery,
          null);
    }
  }

  public record Newspaper(
      @NotNull @Size(max = 30) String issue,
      @NotNull @Size(max = 100) String headline,
      @NotNull @Size(max = 200) String groomProfile,
      @NotNull @Size(max = 200) String brideProfile,
      @NotNull @Size(max = 200) String groomAnswer,
      @NotNull @Size(max = 200) String brideAnswer,
      @NotNull @Size(max = 300) String preparation,
      @NotNull @Size(max = 200) String futurePlans,
      @NotNull @Size(max = 1000) String familyLetter,
      @NotNull @Size(min = 8, max = 8) List<@NotNull @Size(max = 30) String> familyNames,
      @NotNull @Size(min = 6, max = 6) List<@NotNull @Size(max = 100) String> memories) {}

  public record GiftAccounts(
      boolean enabled,
      @NotBlank @Size(max = 50) String title,
      @NotNull @Size(max = 500) String message,
      @NotNull @Size(max = 8) List<@NotNull @Valid GiftAccount> accounts) {}

  public record GiftAccount(
      @NotNull @Pattern(regexp = "GROOM|BRIDE") String side,
      @NotBlank @Size(max = 30) String relation,
      @NotBlank @Size(max = 40) String bank,
      @NotBlank @Size(max = 30) String holder,
      @NotBlank @Pattern(regexp = "(?=.{5,40}$)[0-9]+(?:[ -][0-9]+)*") String number) {}

  public record Design(
      boolean enabled,
      @Min(360) @Max(1000) int height,
      @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String background,
      boolean photoBackground,
      @NotNull @Pattern(regexp = "SERIF|SANS|PEN|BRUSH|DODUM|BATANG|SYSTEM") String font,
      @NotNull @Size(max = 30) List<@NotNull @Valid TextLayer> texts,
      @NotNull @Size(max = 5) List<@NotNull @Valid QrLayer> qrs) {}

  public record TextLayer(
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,50}") String id,
      @NotNull @Size(max = 500) String text,
      @NotNull @Pattern(regexp = "NONE|NAMES|GROOM|BRIDE|DATE|VENUE|GREETING") String binding,
      @Min(0) @Max(360) int x,
      @Min(0) @Max(1000) int y,
      @Min(40) @Max(360) int width,
      @Min(8) @Max(60) int size,
      @NotNull @Pattern(regexp = "SERIF|SANS|PEN|BRUSH|DODUM|BATANG|SYSTEM") String font,
      @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String color,
      @NotNull @Pattern(regexp = "LEFT|CENTER|RIGHT") String align,
      boolean bold) {}

  public record QrLayer(
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,50}") String id,
      @Min(0) @Max(360) int x,
      @Min(0) @Max(1000) int y,
      @Min(60) @Max(200) int size,
      @NotNull @Pattern(regexp = "SELF|URL") String target,
      @NotNull @Size(max = 500) String url,
      @NotNull @Pattern(regexp = "SQUARE|ROUNDED|DOTS") String style,
      @NotNull @Pattern(regexp = "SQUARE|ROUNDED|CIRCLE") String frame) {}

  public record Card(String id, String token, boolean published, int version, Body data) {}

  public record Summary(String id, String groom, String bride, boolean published) {}

  public record Save(@Min(1) int version, @NotNull @Valid Body data) {}

  public record Publish(@Min(1) int version, boolean published) {}
}
