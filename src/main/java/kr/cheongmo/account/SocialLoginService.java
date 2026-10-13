package kr.cheongmo.account;

import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.userinfo.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.user.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

@Service
public class SocialLoginService {
  private final List<ClientRegistration> registrations = new ArrayList<>();
  private final AccountService accountService;

  public SocialLoginService(Environment environment, AccountService accountService) {
    this.accountService = accountService;
    for (String provider : List.of("kakao", "google", "naver")) {
      String prefix = provider.toUpperCase(Locale.ROOT);
      String id = environment.getProperty(prefix + "_CLIENT_ID", ""),
          secret = environment.getProperty(prefix + "_CLIENT_SECRET", "");
      if (id.isBlank() || secret.isBlank()) continue;
      ClientRegistration.Builder b;
      if (provider.equals("google"))
        b = CommonOAuth2Provider.GOOGLE.getBuilder(provider).scope("profile");
      else {
        boolean kakao = provider.equals("kakao");
        b =
            ClientRegistration.withRegistrationId(provider)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri(
                    kakao
                        ? "https://kauth.kakao.com/oauth/authorize"
                        : "https://nid.naver.com/oauth2.0/authorize")
                .tokenUri(
                    kakao
                        ? "https://kauth.kakao.com/oauth/token"
                        : "https://nid.naver.com/oauth2.0/token")
                .userInfoUri(
                    kakao
                        ? "https://kapi.kakao.com/v2/user/me"
                        : "https://openapi.naver.com/v1/nid/me")
                .userNameAttributeName(kakao ? "id" : "response")
                .clientName(provider);
        if (kakao) b.scope("profile_nickname");
      }
      registrations.add(b.clientId(id).clientSecret(secret).build());
    }
  }

  public OAuth2UserService<OAuth2UserRequest, OAuth2User> userService() {
    var delegate = new DefaultOAuth2UserService();
    return request -> {
      var user = delegate.loadUser(request);
      return resolve(request.getClientRegistration().getRegistrationId(), user.getAttributes());
    };
  }

  public OAuth2User resolve(String provider, Map<String, Object> attributes) {
    Map<?, ?> profile = attributes;
    if (provider.equals("naver"))
      profile = attributes.get("response") instanceof Map<?, ?> m ? m : Map.of();
    Object subject = profile.get(provider.equals("google") ? "sub" : "id");
    if (subject == null || subject.toString().isBlank() || subject.toString().length() > 255)
      throw new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info"));
    Object name = profile.get(provider.equals("google") ? "name" : "nickname");
    if (provider.equals("kakao") && attributes.get("properties") instanceof Map<?, ?> properties)
      name = properties.get("nickname");
    if (name == null) name = profile.get("name");
    var account =
        accountService.social(provider, subject.toString(), name == null ? null : name.toString());
    return new DefaultOAuth2User(
        List.of(new SimpleGrantedAuthority("ROLE_USER")),
        Map.of("appUsername", account.username()),
        "appUsername");
  }

  public List<ClientRegistration> registrations() {
    return List.copyOf(registrations);
  }

  public List<Map<String, Object>> providers() {
    return List.of("kakao", "google", "naver").stream()
        .map(
            p ->
                Map.<String, Object>of(
                    "id",
                    p,
                    "enabled",
                    registrations.stream().anyMatch(r -> r.getRegistrationId().equals(p))))
        .toList();
  }
}

