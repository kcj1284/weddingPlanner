package kr.cheongmo;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
@Configuration
class SecurityConfig {
    @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(); }
    @Bean SecurityFilterChain chain(HttpSecurity http,SocialLogin social) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/", "/index.html", "/styles.css", "/design-refinements.css", "/app.js", "/favicon.svg", "/api/csrf", "/api/config", "/api/register", "/api/login", "/error").permitAll()
            .requestMatchers("/api/auth/providers","/api/guest-preview/**","/api/guest-session","/oauth2/**","/login/oauth2/**").permitAll()
            .anyRequest().authenticated())
            .formLogin(form -> form.loginProcessingUrl("/api/login")
                .successHandler((req,res,a)->{res.setStatus(204);})
                .failureHandler((req,res,e)->{res.setStatus(401); res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"message\":\"아이디와 비밀번호를 확인해 주세요.\"}");}))
            .logout(logout -> logout.logoutUrl("/api/logout").logoutSuccessHandler((req,res,a)->res.setStatus(204)))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((req,res,e)->{res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"message\":\"로그인이 필요합니다.\"}");})
                .accessDeniedHandler((req,res,e)->{res.setStatus(403);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"message\":\"권한이 없거나 요청이 만료되었습니다. 새로고침해 주세요.\"}");}))
            .headers(headers -> headers.contentSecurityPolicy(csp->csp.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")));
        http.headers(h->h.referrerPolicy(r->r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
        if(!social.registrations.isEmpty())http.oauth2Login(o->o
            .clientRegistrationRepository(new org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository(social.registrations))
            .authorizedClientRepository(new org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository())
            .loginPage("/").userInfoEndpoint(u->u.userService(social.userService()))
            .successHandler((req,res,a)->res.sendRedirect("/?social=ok"))
            .failureHandler((req,res,e)->res.sendRedirect("/?authError=1")));
        return http.build();
    }
}
