package kr.cheongmo.account.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public final class RecoveryDtos {
  private RecoveryDtos() {}

  public record EmailRequest(
      @NotBlank @Email @Size(max = 254) String email, @Size(max = 128) String password) {}

  public record TokenRequest(@NotBlank @Size(max = 100) String token) {}

  public record ResetRequest(
      @NotBlank @Size(max = 100) String token, @NotNull @Size(max = 128) String password) {}

  public record Status(boolean verified, String email) {}

  public record FoundId(String maskedId, String message) {}

  public record MailRequested(String message, boolean localMailbox) {}

  public record Message(String message) {}
}
