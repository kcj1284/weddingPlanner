package kr.cheongmo;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import kr.cheongmo.common.CredentialPolicy;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class CredentialPolicyIntegrationTest extends MeetingIntegrationTest {
  @Autowired WebApplicationContext context;
  @Autowired PasswordEncoder encoder;
  @Autowired tools.jackson.databind.ObjectMapper json;
  MockMvc mvc;

  @BeforeEach
  void setupPolicy() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  String body(String username, String password) throws Exception {
    return json.writeValueAsString(
        java.util.Map.of("username", username, "displayName", "규칙 검증", "password", password));
  }

  @Test
  void signupRejectsBadIdsAndShortPasswordsAndAcceptsEightRepeatedOrSequentialCharacters()
      throws Exception {
    for (String id : new String[] {"abcd", "ABCDE", "has space", "a".repeat(21)})
      mvc.perform(
              post("/api/register")
                  .with(csrf())
                  .contentType("application/json")
                  .content(body(id, "12345678")))
          .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/register")
                .with(csrf())
                .contentType("application/json")
                .content(body("valid_id", "1234567")))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/register")
                .with(csrf())
                .contentType("application/json")
                .content(body("valid-id", "12345678")))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/register")
                .with(csrf())
                .contentType("application/json")
                .content(body("repeat_id", "aaaaaaaa")))
        .andExpect(status().isOk());
  }

  @Test
  void unicodeAndPassphrasesUpTo64CharactersAreStoredWithoutTruncation() throws Exception {
    String password = "한글".repeat(32);
    mvc.perform(
            post("/api/register")
                .with(csrf())
                .contentType("application/json")
                .content(body("korean_id", password)))
        .andExpect(status().isOk());
    String hash =
        db.queryForObject(
            "SELECT password_hash FROM app_user WHERE username='korean_id'", String.class);
    assertThat(hash).startsWith("{pbkdf2}");
    assertThat(encoder.matches(password, hash)).isTrue();
    assertThat(encoder.matches(password.substring(0, 63) + "다", hash)).isFalse();
    mvc.perform(
            post("/api/register")
                .with(csrf())
                .contentType("application/json")
                .content(body("too_long_id", "한".repeat(65))))
        .andExpect(status().isBadRequest());
    CredentialPolicy.password("😀".repeat(64));
    CredentialPolicy.password("반복 단어 반복 단어");
  }

  @Test
  void oldBcryptCredentialsStillLogInWithoutApplyingNewSignupRules() throws Exception {
    String legacy =
        new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
            .encode("password123");
    db.update("UPDATE app_user SET password_hash=? WHERE id=?", legacy, host);
    mvc.perform(
            post("/api/login")
                .with(csrf())
                .param("username", "host")
                .param("password", "password123"))
        .andExpect(status().isNoContent());
    mvc.perform(
            post("/api/login")
                .with(csrf())
                .param("username", "host")
                .param("password", "한".repeat(64)))
        .andExpect(status().isUnauthorized());
    assertThat(encoder.matches("aaaaaaaa", encoder.encode("aaaaaaaa"))).isTrue();
  }
}
