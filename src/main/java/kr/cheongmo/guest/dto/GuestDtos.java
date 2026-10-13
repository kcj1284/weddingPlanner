package kr.cheongmo.guest.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public final class GuestDtos {
  private GuestDtos() {}

  public record Label(@NotBlank @Size(max = 30) String label) {}

  public record Entry(@NotBlank String token, @Size(max = 30) String displayName) {}
}
