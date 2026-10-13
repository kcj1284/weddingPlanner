package kr.cheongmo.account;

import java.util.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.userinfo.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.user.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class SocialLoginController {
  private final SocialLoginService socialLoginService;

  public SocialLoginController(SocialLoginService socialLoginService) {
    this.socialLoginService = socialLoginService;
  }

  @GetMapping("/api/auth/providers")
  public List<Map<String, Object>> providers() {
    return socialLoginService.providers();
  }
}
