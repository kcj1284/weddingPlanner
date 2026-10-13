package kr.cheongmo.guest.dto;

import jakarta.validation.constraints.*;
import java.util.*;
import kr.cheongmo.account.dto.AccountDtos;

public final class GroupInviteDtos {
  private GroupInviteDtos() {}

  public record Entry(@NotBlank String token, @Size(max = 30) String displayName) {}

  public record Joined(AccountDtos.Account account, String meetingId, String personalToken) {}
}
