package kr.cheongmo.account;

import jakarta.validation.constraints.*;
import java.util.*;
import kr.cheongmo.account.dto.AccountDtos;
import kr.cheongmo.common.CredentialPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Service
public class AccountService implements UserDetailsService {
  private final AccountRepository repository;
  private final PasswordEncoder passwordEncoder;

  public AccountService(AccountRepository repository, PasswordEncoder passwordEncoder) {
    this.repository = repository;
    this.passwordEncoder = passwordEncoder;
  }

  public AccountDtos.Account registerHost(AccountDtos.Registration body) {
    CredentialPolicy.username(body.username());
    CredentialPolicy.password(body.password());
    return register(body);
  }

  public AccountDtos.Account register(AccountDtos.Registration body) {
    String id = UUID.randomUUID().toString();
    repository.insertAccount(
        id, body.username(), body.displayName().trim(), passwordEncoder.encode(body.password()));
    return new AccountDtos.Account(id, body.username(), body.displayName().trim(), false);
  }

  public AccountDtos.Account account(String name) {
    return repository.findAccount(name).stream()
        .findFirst()
        .orElseThrow(() -> new UsernameNotFoundException("계정을 확인해 주세요."));
  }

  public int version(String name) {
    return repository.findAuthVersion(name);
  }

  public void rememberLogin(jakarta.servlet.http.HttpServletRequest request, String name) {
    request.getSession().setAttribute("accountVersion", version(name));
    request.getSession().setAttribute("accountVersionUser", name);
  }

  @Override
  public UserDetails loadUserByUsername(String name) {
    return repository.findLoginUser(name).stream()
        .findFirst()
        .orElseThrow(() -> new UsernameNotFoundException("계정을 확인해 주세요."));
  }

  @Transactional
  public AccountDtos.Account guest(String name) {
    AccountDtos.Account a =
        register(
            new AccountDtos.Registration(
                "g_" + UUID.randomUUID().toString().replace("-", "").substring(0, 26),
                name,
                UUID.randomUUID().toString()));
    repository.insertGuestKind(a.id());
    return new AccountDtos.Account(a.id(), a.username(), a.displayName(), true);
  }

  @Transactional
  public AccountDtos.Account social(String provider, String subject, String name) {
    var found = repository.findSocialUsername(provider, subject);
    if (!found.isEmpty()) return account(found.getFirst());
    String display = (name == null || name.isBlank()) ? "호스트" : name.strip();
    display = display.substring(0, Math.min(display.length(), 30));
    AccountDtos.Account a =
        register(
            new AccountDtos.Registration(
                "s_" + UUID.randomUUID().toString().replace("-", "").substring(0, 26),
                display,
                UUID.randomUUID().toString()));
    repository.insertSocialIdentity(provider, subject, a.id());
    return a;
  }
}
