package kr.cheongmo.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.*;
import kr.cheongmo.account.dto.AccountDtos;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AccountController {
  private final AccountService accountService;
  private final Environment environment;

  public AccountController(AccountService accountService, Environment environment) {
    this.accountService = accountService;
    this.environment = environment;
  }

  @GetMapping("/csrf")
  public AccountDtos.Csrf csrf(CsrfToken token) {
    return new AccountDtos.Csrf(token.getToken(), token.getHeaderName());
  }

  @GetMapping("/config")
  public AccountDtos.Config config() {
    return new AccountDtos.Config(environment.matchesProfiles("local"));
  }

  @GetMapping("/me")
  public AccountDtos.Account me(Principal principal) {
    return accountService.account(principal.getName());
  }

  @PostMapping("/register")
  public AccountDtos.Account register(@Valid @RequestBody AccountDtos.Registration body) {
    return accountService.registerHost(body);
  }
}

