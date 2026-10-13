package kr.cheongmo.account.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public final class AccountDtos {
  private AccountDtos() {}

  public record Account(String id, String username, String displayName, boolean guest) {}

  public record Csrf(String token, String headerName) {}

  public record Config(boolean demoEnabled) {}

  public record Registration(
      @Pattern(regexp = "[a-z0-9_-]{3,30}") @NotNull String username,
      @NotBlank @Size(max = 30) String displayName,
      @NotNull @Size(min = 8, max = 128) String password,
      @Email @Size(max = 254) String recoveryEmail) {
    public Registration(String username, String displayName, String password) {
      this(username, displayName, password, null);
    }
  }
}
