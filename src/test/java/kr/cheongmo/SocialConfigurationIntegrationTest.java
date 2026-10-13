package kr.cheongmo;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
    properties = {
      "KAKAO_CLIENT_ID=test-client",
      "KAKAO_CLIENT_SECRET=test-secret",
      "GOOGLE_CLIENT_ID=test-client",
      "GOOGLE_CLIENT_SECRET=test-secret",
      "NAVER_CLIENT_ID=test-client",
      "NAVER_CLIENT_SECRET=test-secret"
    })
class SocialConfigurationIntegrationTest extends MeetingIntegrationTest {
  @Autowired WebApplicationContext context;

  @Test
  void configuredProvidersStartStateProtectedAuthorizationWithoutNetworkCalls() throws Exception {
    var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    mvc.perform(get("/api/auth/providers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].enabled").value(true))
        .andExpect(jsonPath("$[2].enabled").value(true));
    String[] hosts = {
      "https://kauth.kakao.com/oauth/authorize?",
      "https://accounts.google.com/o/oauth2/v2/auth?",
      "https://nid.naver.com/oauth2.0/authorize?"
    };
    int index = 0;
    for (String provider : java.util.List.of("kakao", "google", "naver")) {
      var result =
          mvc.perform(get("/oauth2/authorization/" + provider))
              .andExpect(status().is3xxRedirection())
              .andReturn();
      org.assertj.core.api.Assertions.assertThat(result.getResponse().getRedirectedUrl())
          .startsWith(hosts[index++])
          .contains("state=", "redirect_uri=");
    }
    mvc.perform(get("/api/meetings")).andExpect(status().isUnauthorized());
  }
}
