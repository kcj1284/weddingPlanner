package kr.cheongmo.place.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public final class PlaceDtos {
  private PlaceDtos() {}

  public record Cached(Map<String, Object> value, boolean fresh) {}
}
