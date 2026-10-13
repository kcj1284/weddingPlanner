package kr.cheongmo.guest;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import kr.cheongmo.account.AccountService;
import kr.cheongmo.guest.dto.GroupInviteDtos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class GroupInviteController {
  private final GroupInviteService groupInviteService;
  private final AccountService accountService;
  private final GuestAttemptLimiter guestAttemptLimiter;
  private final String kakaoJavascriptKey;

  public GroupInviteController(
      GroupInviteService groupInviteService,
      AccountService accountService,
      GuestAttemptLimiter guestAttemptLimiter,
      @Value("${cheongmo.kakao-javascript-key:}") String kakaoJavascriptKey) {
    this.groupInviteService = groupInviteService;
    this.accountService = accountService;
    this.guestAttemptLimiter = guestAttemptLimiter;
    this.kakaoJavascriptKey = kakaoJavascriptKey;
  }

  @GetMapping("/kakao-share-config")
  public Map<String, String> config() {
    return Map.of("javascriptKey", kakaoJavascriptKey);
  }

  @PostMapping("/meetings/{id}/group-links")
  public Map<String, Object> create(@PathVariable String id, java.security.Principal authenticatedPrincipal) {
    return groupInviteService.create(id, accountService.account(authenticatedPrincipal.getName()).id());
  }

  @GetMapping("/meetings/{id}/group-links")
  public List<Map<String, Object>> list(@PathVariable String id, java.security.Principal authenticatedPrincipal) {
    return groupInviteService.list(id, accountService.account(authenticatedPrincipal.getName()).id());
  }

  @DeleteMapping("/meetings/{id}/group-links/{invite}")
  public void revoke(
      @PathVariable String id, @PathVariable String invite, java.security.Principal authenticatedPrincipal) {
    groupInviteService.revoke(id, accountService.account(authenticatedPrincipal.getName()).id(), invite);
  }

  @GetMapping("/group-preview/{token}")
  public Map<String, Object> preview(
      @PathVariable String token, HttpServletRequest request, java.security.Principal authenticatedPrincipal) {
    guestAttemptLimiter.check(request.getRemoteAddr());
    return groupInviteService.preview(token, authenticatedPrincipal == null ? null : accountService.account(authenticatedPrincipal.getName()));
  }

  @PostMapping("/group-session")
  public Map<String, String> enter(
      @Valid @RequestBody GroupInviteDtos.Entry input,
      HttpServletRequest request,
      HttpServletResponse response,
      java.security.Principal authenticatedPrincipal) {
    guestAttemptLimiter.check(request.getRemoteAddr());
    var joined =
        groupInviteService.enter(
            input.token(), input.displayName(), authenticatedPrincipal == null ? null : accountService.account(authenticatedPrincipal.getName()));
    var account = joined.account();
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
    return Map.of("meetingId", joined.meetingId(), "guestToken", joined.personalToken());
  }
}
