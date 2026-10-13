package kr.cheongmo.account;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AccountVersionFilter extends OncePerRequestFilter {
  private final AccountService accountService;

  public AccountVersionFilter(AccountService accountService) {
    this.accountService = accountService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    var session = request.getSession(false);
    if (request.getRequestURI().startsWith("/api/")
        && session != null
        && auth != null
        && auth.isAuthenticated()
        && !(auth instanceof AnonymousAuthenticationToken)) {
      int version = accountService.version(auth.getName());
      Object remembered = session.getAttribute("accountVersion");
      if (remembered == null || !auth.getName().equals(session.getAttribute("accountVersionUser")))
        accountService.rememberLogin(request, auth.getName());
      else if (!remembered.equals(version)) {
        session.invalidate();
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(request, response);
  }
}
