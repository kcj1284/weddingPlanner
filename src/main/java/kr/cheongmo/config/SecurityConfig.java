package kr.cheongmo.config;

import kr.cheongmo.account.AccountService;
import kr.cheongmo.account.AccountVersionFilter;
import kr.cheongmo.account.SocialLoginService;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  public PasswordEncoder passwordEncoder() {
    var pbkdf2 =
        new org.springframework.security.crypto.password.Pbkdf2PasswordEncoder("", 16, 600000, 256);
    pbkdf2.setAlgorithm(
        org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm
            .PBKDF2WithHmacSHA256);
    var bcryptEncoder = new BCryptPasswordEncoder();
    PasswordEncoder bcrypt =
        new PasswordEncoder() {
          public String encode(CharSequence raw) {
            return bcryptEncoder.encode(raw);
          }

          public boolean matches(CharSequence raw, String encoded) {
            return raw != null
                && raw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72
                && bcryptEncoder.matches(raw, encoded);
          }
        };
    var passwordEncoder =
        new org.springframework.security.crypto.password.DelegatingPasswordEncoder(
            "pbkdf2", java.util.Map.of("pbkdf2", pbkdf2, "bcrypt", bcrypt));
    passwordEncoder.setDefaultPasswordEncoderForMatches(bcrypt);
    return passwordEncoder;
  }

  @Bean
  public SecurityFilterChain chain(
      HttpSecurity http,
      SocialLoginService social,
      AccountService accountService,
      org.springframework.core.env.Environment environment)
      throws Exception {
    // The provider follows the page protocol; production uses HTTPS, local development uses HTTP.
    String postcodeFrames =
        " https://postcode.map.kakao.com"
            + (environment.matchesProfiles("local") ? " http://postcode.map.kakao.com" : "");
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/account.html",
                        "/account.js",
                        "/account.css",
                        "/credential-policy.js",
                        "/credential-feedback.js",
                        "/credential-feedback.css",
                        "/api/account/find-id",
                        "/api/account/reset-request",
                        "/api/account/verify",
                        "/api/account/reset")
                    .permitAll()
                    .requestMatchers(
                        "/",
                        "/index.html",
                        "/styles.css",
                        "/design-refinements.css",
                        "/app.js",
                        "/js/**",
                        "/favicon.svg",
                        "/api/csrf",
                        "/api/config",
                        "/api/register",
                        "/api/login",
                        "/error")
                    .permitAll()
                    .requestMatchers(
                        "/invitation.html",
                        "/invitation.js",
                        "/invitation-designer.js",
                        "/invitation-ux.js",
                        "/invitation-address.js",
                        "/invitation-address-frame.css",
                        "/invitation-accounts.js",
                        "/invitation-newspaper.js",
                        "/newspaper-pages.js",
                        "/newspaper-source.css",
                        "/newspaper.css",
                        "/newspaper-assets/**",
                        "/invitation.css",
                        "/studio-refinements.css",
                        "/icons/**",
                        "/fonts/**",
                        "/api/invitation-qr",
                        "/api/public-invitations/**")
                    .permitAll()
                    .requestMatchers(
                        "/api/auth/providers",
                        "/api/guest-preview/**",
                        "/api/guest-session",
                        "/api/group-preview/**",
                        "/api/group-session",
                        "/oauth2/**",
                        "/login/oauth2/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(
            form ->
                form.loginProcessingUrl("/api/login")
                    .successHandler(
                        (req, res, a) -> {
                          accountService.rememberLogin(req, a.getName());
                          res.setStatus(204);
                        })
                    .failureHandler(
                        (req, res, e) -> {
                          res.setStatus(401);
                          res.setContentType("application/json;charset=UTF-8");
                          res.getWriter().write("{\"message\":\"아이디와 비밀번호를 확인해 주세요.\"}");
                        }))
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/logout")
                    .logoutSuccessHandler((req, res, a) -> res.setStatus(204)))
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (req, res, e) -> {
                          res.setStatus(401);
                          res.setContentType("application/json;charset=UTF-8");
                          res.getWriter().write("{\"message\":\"로그인이 필요합니다.\"}");
                        })
                    .accessDeniedHandler(
                        (req, res, e) -> {
                          res.setStatus(403);
                          res.setContentType("application/json;charset=UTF-8");
                          res.getWriter()
                              .write("{\"message\":\"권한이 없거나 요청이 만료되었습니다. 새로고침해 주세요.\"}");
                        }))
        .headers(
            headers ->
                headers.contentSecurityPolicy(
                    csp ->
                        csp.policyDirectives(
                            "default-src 'self'; script-src 'self' https://t1.kakaocdn.net;"
                                + " style-src 'self'; img-src 'self' data:; connect-src 'self'"
                                + " https://kapi.kakao.com https://kauth.kakao.com"
                                + " https://sharer.kakao.com; frame-src https://kauth.kakao.com"
                                + " https://sharer.kakao.com"
                                + postcodeFrames
                                + "; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"
                                + " https://sharer.kakao.com")));
    http.headers(
        h ->
            h.referrerPolicy(
                r ->
                    r.policy(
                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                            .ReferrerPolicy.NO_REFERRER)));
    if (!social.registrations().isEmpty())
      http.oauth2Login(
          o ->
              o.clientRegistrationRepository(
                      new org.springframework.security.oauth2.client.registration
                          .InMemoryClientRegistrationRepository(social.registrations()))
                  .authorizedClientRepository(
                      new org.springframework.security.oauth2.client.web
                          .HttpSessionOAuth2AuthorizedClientRepository())
                  .loginPage("/")
                  .userInfoEndpoint(u -> u.userService(social.userService()))
                  .successHandler(
                      (req, res, a) -> {
                        accountService.rememberLogin(req, a.getName());
                        res.sendRedirect("/?social=ok");
                      })
                  .failureHandler((req, res, e) -> res.sendRedirect("/?authError=1")));
    http.addFilterAfter(
        new AccountVersionFilter(accountService),
        org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class);
    return http.build();
  }
}
