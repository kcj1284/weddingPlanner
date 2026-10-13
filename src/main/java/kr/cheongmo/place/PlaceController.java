package kr.cheongmo.place;

import java.security.Principal;
import java.util.*;
import kr.cheongmo.account.AccountService;
import org.springframework.web.bind.annotation.*;

@RestController
public class PlaceController {
  private final PlaceService placeService;
  private final AccountService accountService;

  public PlaceController(PlaceService placeService, AccountService accountService) {
    this.placeService = placeService;
    this.accountService = accountService;
  }

  @GetMapping("/api/meetings/{id}/places-reviews")
  public Map<String, Object> reviews(
      Principal authenticatedPrincipal,
      @PathVariable String id,
      @RequestParam("q") String query,
      @RequestParam(defaultValue = "FD6") String type) {
    return placeService.reviews(id, accountService.account(authenticatedPrincipal.getName()).id(), query, type);
  }

  @GetMapping("/api/meetings/{id}/places")
  public Map<String, Object> search(
      Principal authenticatedPrincipal,
      @PathVariable String id,
      @RequestParam("q") String query,
      @RequestParam(defaultValue = "FD6") String type) {
    return placeService.search(id, accountService.account(authenticatedPrincipal.getName()).id(), query, type);
  }
}
