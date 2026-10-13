package kr.cheongmo.account;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import kr.cheongmo.account.dto.RecoveryDtos;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
public class AccountRecoveryController {
  private final AccountRecoveryService accountRecoveryService;
  private final RecoveryMailService recoveryMailService;
  private final AccountRecoveryMailService accountRecoveryMailService;

  public AccountRecoveryController(
      AccountRecoveryService accountRecoveryService,
      RecoveryMailService recoveryMailService,
      AccountRecoveryMailService accountRecoveryMailService) {
    this.accountRecoveryService = accountRecoveryService;
    this.recoveryMailService = recoveryMailService;
    this.accountRecoveryMailService = accountRecoveryMailService;
  }

  public RecoveryDtos.MailRequested sent() {
    return new RecoveryDtos.MailRequested(
        "등록된 이메일과 일치하면 안내 메일을 요청합니다. 메일이 오지 않으면 잠시 후 다시 시도해 주세요.", recoveryMailService.local());
  }

  @GetMapping("/recovery-status")
  public RecoveryDtos.Status status(java.security.Principal authenticatedPrincipal) {
    return accountRecoveryService.status(authenticatedPrincipal.getName());
  }

  @PostMapping("/verify-request")
  public RecoveryDtos.MailRequested request(
      @Valid @RequestBody RecoveryDtos.EmailRequest input,
      java.security.Principal authenticatedPrincipal,
      HttpServletRequest request) {
    accountRecoveryService.limit(request.getRemoteAddr(), input.email());
    accountRecoveryService.requestVerification(authenticatedPrincipal.getName(), input.email(), input.password());
    return new RecoveryDtos.MailRequested("인증 메일을 보냈어요. 링크를 열어 인증을 완료해 주세요.", recoveryMailService.local());
  }

  @PostMapping("/find-id")
  public RecoveryDtos.FoundId find(
      @Valid @RequestBody RecoveryDtos.EmailRequest input, HttpServletRequest request) {
    accountRecoveryService.limit(request.getRemoteAddr(), input.email());
    return accountRecoveryService.findId(input.email());
  }

  @PostMapping("/reset-request")
  public RecoveryDtos.MailRequested resetRequest(
      @Valid @RequestBody RecoveryDtos.EmailRequest input, HttpServletRequest request) {
    accountRecoveryMailService.requestReset(request.getRemoteAddr(), input.email());
    return sent();
  }

  @PostMapping("/verify")
  public RecoveryDtos.Message verify(@Valid @RequestBody RecoveryDtos.TokenRequest input) {
    accountRecoveryService.verify(input.token());
    return new RecoveryDtos.Message("이메일 인증을 완료했어요. 이제 아이디 찾기와 비밀번호 재설정을 사용할 수 있어요.");
  }

  @PostMapping("/reset")
  public RecoveryDtos.Message reset(@Valid @RequestBody RecoveryDtos.ResetRequest input) {
    accountRecoveryService.reset(input.token(), input.password());
    return new RecoveryDtos.Message("비밀번호를 변경했어요. 모든 기기에서 다시 로그인해 주세요.");
  }
}
