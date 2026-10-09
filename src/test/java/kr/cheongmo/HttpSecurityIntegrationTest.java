package kr.cheongmo;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HttpSecurityIntegrationTest extends MeetingIntegrationTest {
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    @Autowired GuestAccess links;
    @Autowired SocialLogin social;
    @BeforeEach void configureHttp() { mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    @Test void bearerGuestSessionPersistsAndRevocationRemovesAccess() throws Exception {
        var link=links.create(meeting,host,"민지");String token=(String)link.get("token");
        mvc.perform(get("/api/guest-preview/"+token)).andExpect(status().isOk());
        mvc.perform(post("/api/guest-session").contentType("application/json").content("{\"token\":\""+token+"\",\"displayName\":\"민지\"}")).andExpect(status().isForbidden());
        var result=mvc.perform(post("/api/guest-session").with(csrf()).contentType("application/json").content("{\"token\":\""+token+"\",\"displayName\":\"민지\"}"))
            .andExpect(status().isOk()).andReturn();
        var session=(org.springframework.mock.web.MockHttpSession)result.getRequest().getSession();
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.guest").value(true));
        mvc.perform(get("/api/meetings/"+meeting).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.inviteToken").doesNotExist());
        mvc.perform(post("/api/meetings").session(session).with(csrf()).contentType("application/json").content("{\"title\":\"불가\",\"area\":\"서울\",\"expectedCount\":3}"))
            .andExpect(status().isForbidden());
        links.revoke(meeting,host,(String)link.get("id"));
        mvc.perform(get("/api/meetings/"+meeting).session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/guest-preview/"+token)).andExpect(status().isNotFound());
    }
    @Test void providerProfilesResolveToStableHostAccounts() throws Exception {
        mvc.perform(get("/api/auth/providers")).andExpect(status().isOk()).andExpect(jsonPath("$[2].id").value("naver"));
        var kakao=social.resolve("kakao",java.util.Map.of("id",123,"properties",java.util.Map.of("nickname","카카오 호스트")));
        var google=social.resolve("google",java.util.Map.of("sub","123","name","구글 호스트"));
        var naver=social.resolve("naver",java.util.Map.of("response",java.util.Map.of("id","123","nickname","네이버 호스트")));
        org.assertj.core.api.Assertions.assertThat(kakao.getName()).isNotEqualTo(google.getName()).isNotEqualTo(naver.getName());
        org.assertj.core.api.Assertions.assertThat(accounts.account(naver.getName()).displayName()).isEqualTo("네이버 호스트");
        org.assertj.core.api.Assertions.assertThat(social.resolve("kakao",java.util.Map.of("id",123)).getName()).isEqualTo(kakao.getName());
    }
    @Test void authenticationCsrfMembershipAndValidationAreEnforcedAtHttpBoundary() throws Exception {
        mvc.perform(get("/api/meetings")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/meetings").with(user("host")).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/meetings/"+meeting).with(user("outside"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/meetings/"+meeting+"/options").with(user("guest")).with(csrf())
            .contentType("application/json").content("{\"kind\":\"PLACE\",\"title\":\"장소\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/meetings").with(user("host")).with(csrf()).contentType("application/json")
            .content("{\"title\":\"청모\",\"area\":\"서울\",\"expectedCount\":1}"))
            .andExpect(status().isBadRequest());
    }
    @Test void signupValidatesInputAndRejectsDuplicateUsername() throws Exception {
        mvc.perform(post("/api/register").with(csrf()).contentType("application/json")
            .content("{\"username\":\"new_user\",\"displayName\":\"새친구\",\"password\":\"password123\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("새친구"))
            .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(post("/api/register").with(csrf()).contentType("application/json")
            .content("{\"username\":\"new_user\",\"displayName\":\"새친구\",\"password\":\"password123\"}"))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/register").with(csrf()).contentType("application/json")
            .content("{\"username\":\"a\",\"displayName\":\"\",\"password\":\"short\"}"))
            .andExpect(status().isBadRequest());
    }
}
