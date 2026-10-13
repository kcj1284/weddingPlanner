package kr.cheongmo;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.autoconfigure.exclude=")
@AutoConfigureMockMvc
class PersistentSessionIntegrationTest extends MeetingIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcIndexedSessionRepository sessions;

  @Test
  void passwordVersionChangeInvalidatesPersistedAuthentication() throws Exception {
    var result =
        mvc.perform(
                post("/api/login")
                    .with(csrf())
                    .param("username", "host")
                    .param("password", "password123"))
            .andExpect(status().isNoContent())
            .andReturn();
    var cookie = result.getResponse().getCookie("SESSION");
    assertThat(cookie).isNotNull();
    db.update("UPDATE app_user SET auth_version=auth_version+1 WHERE username='host'");
    mvc.perform(get("/api/me").cookie(cookie)).andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedIdentityCanBeLoadedFromDatabaseWithANewRequest() throws Exception {
    var result =
        mvc.perform(
                post("/api/login")
                    .with(csrf())
                    .param("username", "host")
                    .param("password", "password123"))
            .andExpect(status().isNoContent())
            .andReturn();
    var cookie = result.getResponse().getCookie("SESSION");
    assertThat(cookie).isNotNull();
    String id =
        new String(
            Base64.getDecoder().decode(cookie.getValue()), java.nio.charset.StandardCharsets.UTF_8);
    assertThat(sessions.findById(id)).isNotNull();
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM spring_session WHERE session_id=?", Integer.class, id))
        .isEqualTo(1);
    mvc.perform(get("/api/me").cookie(cookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("host"));
    mvc.perform(post("/api/logout").cookie(cookie).with(csrf())).andExpect(status().isNoContent());
    mvc.perform(get("/api/me").cookie(cookie)).andExpect(status().isUnauthorized());
    assertThat(sessions.findById(id)).isNull();
  }
}
