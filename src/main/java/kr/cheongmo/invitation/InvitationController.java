package kr.cheongmo.invitation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.*;
import kr.cheongmo.account.AccountService;
import kr.cheongmo.invitation.dto.InvitationDtos;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class InvitationController {
  private final InvitationService invitationService;
  private final AccountService accountService;

  public InvitationController(InvitationService invitationService, AccountService accountService) {
    this.invitationService = invitationService;
    this.accountService = accountService;
  }

  public String owner(Principal authenticatedPrincipal) {
    return accountService.account(authenticatedPrincipal.getName()).id();
  }

  @GetMapping("/invitations")
  public List<InvitationDtos.Summary> list(Principal authenticatedPrincipal) {
    return invitationService.list(owner(authenticatedPrincipal));
  }

  @PostMapping("/invitations")
  public InvitationDtos.Card create(Principal authenticatedPrincipal, @Valid @RequestBody InvitationDtos.Body body) {
    return invitationService.create(owner(authenticatedPrincipal), body);
  }

  @GetMapping("/invitations/{id}")
  public InvitationDtos.Card own(Principal authenticatedPrincipal, @PathVariable String id) {
    return invitationService.own(id, owner(authenticatedPrincipal));
  }

  @PutMapping("/invitations/{id}")
  public InvitationDtos.Card save(
      Principal authenticatedPrincipal, @PathVariable String id, @Valid @RequestBody InvitationDtos.Save body) {
    return invitationService.save(id, owner(authenticatedPrincipal), body.version(), body.data());
  }

  @PutMapping("/invitations/{id}/publication")
  public InvitationDtos.Card publish(
      Principal authenticatedPrincipal, @PathVariable String id, @Valid @RequestBody InvitationDtos.Publish body) {
    return invitationService.publish(id, owner(authenticatedPrincipal), body.version(), body.published());
  }

  @GetMapping("/public-invitations/{token}")
  public ResponseEntity<InvitationDtos.Body> view(@PathVariable String token) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(invitationService.published(token));
  }
}
