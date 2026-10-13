package kr.cheongmo.meeting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.*;
import kr.cheongmo.account.AccountService;
import kr.cheongmo.meeting.dto.MeetingDtos;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class MeetingController {
  private final AccountService accountService;
  private final MeetingService meetingService;

  public MeetingController(AccountService accountService, MeetingService meetingService) {
    this.accountService = accountService;
    this.meetingService = meetingService;
  }

  public String user(Principal authenticatedPrincipal) {
    return accountService.account(authenticatedPrincipal.getName()).id();
  }

  @GetMapping("/meetings")
  public List<MeetingDtos.Summary> list(Principal authenticatedPrincipal) {
    return meetingService.list(user(authenticatedPrincipal));
  }

  @PostMapping("/meetings")
  public MeetingDtos.Created create(Principal authenticatedPrincipal, @Valid @RequestBody MeetingDtos.Create input) {
    return new MeetingDtos.Created(meetingService.create(user(authenticatedPrincipal), input));
  }

  @PostMapping("/join/{token}")
  public MeetingDtos.Created join(Principal authenticatedPrincipal, @PathVariable String token) {
    return new MeetingDtos.Created(meetingService.join(token, user(authenticatedPrincipal)));
  }

  @GetMapping("/meetings/{id}")
  public MeetingDtos.Detail detail(Principal authenticatedPrincipal, @PathVariable String id) {
    return meetingService.detail(id, user(authenticatedPrincipal));
  }

  @PostMapping("/meetings/{id}/options")
  public MeetingDtos.Created option(
      Principal authenticatedPrincipal, @PathVariable String id, @Valid @RequestBody MeetingDtos.OptionInput input) {
    return new MeetingDtos.Created(meetingService.addOption(id, user(authenticatedPrincipal), input));
  }

  @PutMapping("/meetings/{id}/votes")
  public MeetingDtos.Saved vote(
      Principal authenticatedPrincipal, @PathVariable String id, @Valid @RequestBody MeetingDtos.VoteInput input) {
    meetingService.vote(id, user(authenticatedPrincipal), input.optionIds());
    return new MeetingDtos.Saved(true);
  }

  @PostMapping("/meetings/{id}/confirm")
  public MeetingDtos.Saved confirm(
      Principal authenticatedPrincipal, @PathVariable String id, @Valid @RequestBody MeetingDtos.Confirmation input) {
    meetingService.confirm(id, user(authenticatedPrincipal), input.timeId(), input.placeId());
    return new MeetingDtos.Saved(true);
  }

  @PostMapping("/meetings/{id}/reopen")
  public MeetingDtos.Saved reopen(Principal authenticatedPrincipal, @PathVariable String id) {
    meetingService.reopen(id, user(authenticatedPrincipal));
    return new MeetingDtos.Saved(true);
  }

  @PutMapping("/meetings/{id}/options/{option}/reservation")
  public MeetingDtos.Saved reservation(
      Principal authenticatedPrincipal,
      @PathVariable String id,
      @PathVariable String option,
      @Valid @RequestBody MeetingDtos.Reservation input) {
    meetingService.reservation(id, user(authenticatedPrincipal), option, input.status(), input.note());
    return new MeetingDtos.Saved(true);
  }
}

