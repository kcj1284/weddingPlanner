package kr.cheongmo.guest;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.*;
import java.time.*;
import java.util.*;
import kr.cheongmo.account.AccountService;
import kr.cheongmo.guest.dto.GuestDtos;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class GuestAccessController {
  private final GuestAccessService guestAccessService;
  private final AccountService accountService;
  private final GuestAttemptLimiter guestAttemptLimiter;

  public GuestAccessController(
      GuestAccessService guestAccessService, AccountService accountService, GuestAttemptLimiter guestAttemptLimiter) {
    this.guestAccessService = guestAccessService;
    this.accountService = accountService;
    this.guestAttemptLimiter = guestAttemptLimiter;
  }

  @PostMapping("/meetings/{id}/guest-links")
  public Map<String, Object> create(
      @PathVariable String id, java.security.Principal authenticatedPrincipal, @Valid @RequestBody GuestDtos.Label input) {
    return guestAccessService.create(id, accountService.account(authenticatedPrincipal.getName()).id(), input.label());
  }

  @GetMapping("/meetings/{id}/guest-links")
  public List<Map<String, Object>> list(@PathVariable String id, java.security.Principal authenticatedPrincipal) {
    return guestAccessService.list(id, accountService.account(authenticatedPrincipal.getName()).id());
  }

  @GetMapping("/meetings/{id}/guest-links/{linkId}/code")
  public org.springframework.http.ResponseEntity<Map<String, Object>> code(
      @PathVariable String id, @PathVariable String linkId, java.security.Principal authenticatedPrincipal) {
    return org.springframework.http.ResponseEntity.ok()
        .cacheControl(org.springframework.http.CacheControl.noStore())
        .body(guestAccessService.reveal(id, accountService.account(authenticatedPrincipal.getName()).id(), linkId));
  }

  @PostMapping("/meetings/{id}/guest-links/{linkId}/reissue")
  public Map<String, Object> reissue(
      @PathVariable String id, @PathVariable String linkId, java.security.Principal authenticatedPrincipal) {
    return guestAccessService.reissue(id, accountService.account(authenticatedPrincipal.getName()).id(), linkId);
  }

  @DeleteMapping("/meetings/{id}/guest-links/{linkId}")
  public Map<String, Boolean> revoke(
      @PathVariable String id, @PathVariable String linkId, java.security.Principal authenticatedPrincipal) {
    guestAccessService.revoke(id, accountService.account(authenticatedPrincipal.getName()).id(), linkId);
    return Map.of("revoked", true);
  }

  @GetMapping("/guest-preview/{token}")
  public Map<String, Object> preview(@PathVariable String token, HttpServletRequest request) {
    guestAttemptLimiter.check(request.getRemoteAddr());
    return guestAccessService.preview(token);
  }

  @PostMapping("/guest-session")
  public Map<String, String> enter(
      @Valid @RequestBody GuestDtos.Entry input, HttpServletRequest request, HttpServletResponse response) {
    guestAttemptLimiter.check(request.getRemoteAddr());
    var account = guestAccessService.enter(input.token(), input.displayName());
    // Replace any existing identity and rotate its session; this is a bearer-link guest session.
    var previousSession = request.getSession(false);
    if (previousSession != null) previousSession.invalidate();
    request.getSession(true);
    var principal = User.withUsername(account.username()).password("").roles("GUEST").build();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_GUEST"))));
    SecurityContextHolder.setContext(context);
    new HttpSessionSecurityContextRepository().saveContext(context, request, response);
    var guestLink = guestAccessService.link(input.token(), false);
    return Map.of("meetingId", (String) guestLink.get("meetingId"));
  }
}
