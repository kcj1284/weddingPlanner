package kr.cheongmo;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.*;
import java.util.*;
import kr.cheongmo.account.AccountRecoveryService;
import kr.cheongmo.account.dto.AccountDtos;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class AccountRecoveryIntegrationTest extends MeetingIntegrationTest {
  @Autowired AccountRecoveryService recovery;
  @Autowired WebApplicationContext context;
  MockMvc mvc;
  String address;

  @BeforeEach
  void setupRecovery() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    db.update("DELETE FROM recovery_request_limit");
    address = "qa-" + UUID.randomUUID() + "@example.test";
  }

  String mailedToken(String purpose) throws Exception {
    try (var files = Files.list(Path.of("artifacts/test-mailbox"))) {
      return files
          .map(
              path -> {
                try {
                  return Files.readString(path);
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              })
          .filter(
              text ->
                  text.startsWith("To: " + address + "\n") && text.contains("#" + purpose + "="))
          .map(text -> text.split("#" + purpose + "=")[1].split("\\s")[0])
          .findFirst()
          .orElseThrow();
    }
  }

  void verified() throws Exception {
    recovery.requestVerification("host", address, "password123");
    recovery.verify(mailedToken("verify"));
  }

  @Test
  void verificationIsExplicitSingleUseAndGuestCannotBindContact() throws Exception {
    mvc.perform(
            post("/api/account/verify-request")
                .with(user("host"))
                .contentType("application/json")
                .content("{\"email\":\"" + address + "\"}"))
        .andExpect(status().isForbidden());
    recovery.requestVerification("host", address, "password123");
    assertThat(recovery.status("host").verified()).isEqualTo(false);
    String token = mailedToken("verify");
    recovery.verify(token);
    assertThat(recovery.status("host").email()).isEqualTo(address);
    assertThatThrownBy(() -> recovery.verify(token))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    var guestAccount = accounts.guest("초대 게스트");
    assertThatThrownBy(
            () -> recovery.requestVerification(guestAccount.username(), address, "password123"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThat(
            db.queryForObject(
                "SELECT token_hash FROM recovery_token WHERE user_id=?", String.class, host))
        .isNotEqualTo(token);
  }

  @Test
  void lookupReturnsOnlyMaskedIdForVerifiedEmailWithoutSendingMail() throws Exception {
    mvc.perform(
            post("/api/account/find-id")
                .with(csrf())
                .contentType("application/json")
                .content("{\"email\":\"" + address + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.maskedId").value(""));
    verified();
    long mailCount;
    try (var files = Files.list(Path.of("artifacts/test-mailbox"))) {
      mailCount = files.count();
    }
    var present =
        mvc.perform(
                post("/api/account/find-id")
                    .with(csrf())
                    .contentType("application/json")
                    .content("{\"email\":\"" + address.toUpperCase(Locale.ROOT) + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.maskedId").value("****"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(present).doesNotContain("host");
    try (var files = Files.list(Path.of("artifacts/test-mailbox"))) {
      assertThat(files.count()).isEqualTo(mailCount);
    }
    String longId =
        accounts
            .register(new AccountDtos.Registration("wedding_host", "긴 아이디", "password123"))
            .id();
    db.update("UPDATE recovery_contact SET user_id=? WHERE user_id=?", longId, host);
    mvc.perform(
            post("/api/account/find-id")
                .with(csrf())
                .contentType("application/json")
                .content("{\"email\":\"" + address + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.maskedId").value("wedding_****"));
    String shortId =
        accounts.register(new AccountDtos.Registration("abc", "짧은 아이디", "password123")).id();
    db.update("UPDATE recovery_contact SET user_id=? WHERE user_id=?", shortId, longId);
    mvc.perform(
            post("/api/account/find-id")
                .with(csrf())
                .contentType("application/json")
                .content("{\"email\":\"" + address + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.maskedId").value("***"));
  }

  @Test
  void resetChangesPasswordInvalidatesOldSessionsAndCannotReplay() throws Exception {
    verified();
    var login =
        mvc.perform(
                post("/api/login")
                    .with(csrf())
                    .param("username", "host")
                    .param("password", "password123"))
            .andExpect(status().isNoContent())
            .andReturn();
    var oldSession = (MockHttpSession) login.getRequest().getSession();
    recovery.requestReset(address);
    String token = mailedToken("reset");
    mvc.perform(
            post("/api/account/reset")
                .with(csrf())
                .contentType("application/json")
                .content("{\"token\":\"" + token + "\",\"password\":\"newPassword456\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/me").session(oldSession)).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/login")
                .with(csrf())
                .param("username", "host")
                .param("password", "password123"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/login")
                .with(csrf())
                .param("username", "host")
                .param("password", "newPassword456"))
        .andExpect(status().isNoContent());
    assertThatThrownBy(() -> recovery.reset(token, "anotherPassword789"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void expiredAndWrongPurposeTokensCannotResetAndEmailChangeInvalidatesReset() throws Exception {
    recovery.requestVerification("host", address, "password123");
    String verifyToken = mailedToken("verify");
    assertThatThrownBy(() -> recovery.reset(verifyToken, "password987"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    recovery.verify(verifyToken);
    recovery.requestReset(address);
    String resetToken = mailedToken("reset");
    db.update(
        "UPDATE recovery_token SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE"
            + " purpose='RESET'");
    assertThatThrownBy(() -> recovery.reset(resetToken, "password987"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    address = "changed-" + address;
    recovery.requestVerification("host", address, "password123");
    recovery.verify(mailedToken("verify"));
    assertThat(recovery.status("host").email()).isEqualTo(address);
  }

  @Test
  void repeatedMailRequestsAreRateLimited() throws Exception {
    for (int i = 0; i < 5; i++)
      mvc.perform(
              post("/api/account/find-id")
                  .with(csrf())
                  .contentType("application/json")
                  .content("{\"email\":\"" + address + "\"}"))
          .andExpect(status().isOk());
    mvc.perform(
            post("/api/account/find-id")
                .with(csrf())
                .contentType("application/json")
                .content("{\"email\":\"" + address + "\"}"))
        .andExpect(status().isTooManyRequests());
  }
}
