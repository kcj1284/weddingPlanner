package kr.cheongmo;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import kr.cheongmo.invitation.InvitationService;
import kr.cheongmo.invitation.dto.InvitationDtos;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

class InvitationIntegrationTest extends MeetingIntegrationTest {
  @Autowired InvitationService invitations;
  @Autowired ObjectMapper json;
  @Autowired WebApplicationContext context;
  MockMvc mvc;

  InvitationDtos.Body body(String cover) {
    return new InvitationDtos.Body(
        "GARDEN",
        "도윤",
        "지은",
        "김철수·이영희의 아들",
        "박민수·최영희의 딸",
        "함께해 주세요.",
        "2027-05-22T13:00",
        "테스트 웨딩홀",
        "서울 성동구 테스트로 1",
        "지하철 2번 출구",
        "010-1234-5678",
        "",
        cover,
        List.of());
  }

  @BeforeEach
  void configure() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void publicEditorAndViewerAssetsLoadWithoutAuthentication() throws Exception {
    for (String path :
        List.of(
            "/invitation.html",
            "/invitation-ux.js",
            "/invitation-accounts.js",
            "/invitation-newspaper.js",
            "/newspaper-pages.js",
            "/newspaper-source.css",
            "/newspaper.css",
            "/newspaper-assets/6cf57.svg",
            "/studio-refinements.css",
            "/icons/eye.svg",
            "/icons/pencil-line.svg")) mvc.perform(get(path)).andExpect(status().isOk());
  }

  @Test
  void draftPublicationAndRevocationProtectPublicData() throws Exception {
    var c = invitations.create(host, body(""));
    mvc.perform(get("/api/public-invitations/" + c.token())).andExpect(status().isNotFound());
    mvc.perform(get("/api/invitations/" + c.id())).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/invitations/" + c.id()).with(user("outside")))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/api/invitations/" + c.id() + "/publication")
                .with(user("host"))
                .contentType("application/json")
                .content("{\"version\":1,\"published\":true}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/invitations/" + c.id() + "/publication")
                .with(user("host"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"version\":1,\"published\":true}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/public-invitations/" + c.token()))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.groom").value("도윤"))
        .andExpect(jsonPath("$.owner_id").doesNotExist())
        .andExpect(jsonPath("$.token").doesNotExist());
    invitations.publish(c.id(), host, 2, false);
    mvc.perform(get("/api/public-invitations/" + c.token())).andExpect(status().isNotFound());
  }

  @Test
  void staleUpdatesAndOtherHostsCannotOverwriteAnInvitation() throws Exception {
    var c = invitations.create(host, body(""));
    String update = json.writeValueAsString(new InvitationDtos.Save(1, body("")));
    mvc.perform(
            put("/api/invitations/" + c.id())
                .with(user("outside"))
                .with(csrf())
                .contentType("application/json")
                .content(update))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/api/invitations/" + c.id())
                .with(user("host"))
                .with(csrf())
                .contentType("application/json")
                .content(update))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));
    mvc.perform(
            put("/api/invitations/" + c.id())
                .with(user("host"))
                .with(csrf())
                .contentType("application/json")
                .content(update))
        .andExpect(status().isConflict());
    assertThat(invitations.own(c.id(), host).version()).isEqualTo(2);
  }

  @Test
  void guestsAndInvalidPhotosAreRejected() throws Exception {
    var anonymous = accounts.guest("초대 게스트");
    mvc.perform(
            post("/api/invitations")
                .with(user(anonymous.username()))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body(""))))
        .andExpect(status().isForbidden());
    for (String invalid :
        List.of(
            "https://example.com/photo.jpg",
            "data:image/jpeg;base64,PGh0bWw+",
            "data:image/svg+xml;base64,PHN2Zz4="))
      mvc.perform(
              post("/api/invitations")
                  .with(user("host"))
                  .with(csrf())
                  .contentType("application/json")
                  .content(json.writeValueAsString(body(invalid))))
          .andExpect(status().isBadRequest());
    var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(image, "png", bytes);
    var c =
        invitations.create(
            host,
            body(
                "data:image/png;base64,"
                    + java.util.Base64.getEncoder().encodeToString(bytes.toByteArray())));
    assertThat(invitations.own(c.id(), host).data().cover()).startsWith("data:image/png;base64,");
  }

  @Test
  void designCoordinatesFontsAndQrSurviveSaveAndPublication() throws Exception {
    var design =
        new InvitationDtos.Design(
            true,
            640,
            "#faf5ed",
            false,
            "PEN",
            List.of(
                new InvitationDtos.TextLayer(
                    "text1", "우리의 이야기", "NONE", 20, 80, 320, 24, "BRUSH", "#725844", "CENTER",
                    false)),
            List.of(
                new InvitationDtos.QrLayer("qr1", 120, 490, 120, "SELF", "", "DOTS", "CIRCLE")));
    var base = body("");
    var input =
        new InvitationDtos.Body(
            base.template(),
            base.groom(),
            base.bride(),
            base.groomParents(),
            base.brideParents(),
            base.greeting(),
            base.dateTime(),
            base.venue(),
            base.address(),
            base.transport(),
            base.groomPhone(),
            base.bridePhone(),
            base.cover(),
            base.gallery(),
            design);
    var created =
        mvc.perform(
                post("/api/invitations")
                    .with(user("host"))
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(input)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.design.texts[0].x").value(20))
            .andReturn();
    var c = json.readValue(created.getResponse().getContentAsString(), InvitationDtos.Card.class);
    invitations.publish(c.id(), host, c.version(), true);
    mvc.perform(get("/api/public-invitations/" + c.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.design.font").value("PEN"))
        .andExpect(jsonPath("$.design.qrs[0].frame").value("CIRCLE"));
    String invalid = json.writeValueAsString(input).replace("\"x\":20", "\"x\":350");
    mvc.perform(
            post("/api/invitations")
                .with(user("host"))
                .with(csrf())
                .contentType("application/json")
                .content(invalid))
        .andExpect(status().isBadRequest());
    String wrongFont =
        json.writeValueAsString(input).replace("\"font\":\"PEN\"", "\"font\":\"EXTERNAL\"");
    mvc.perform(
            post("/api/invitations")
                .with(user("host"))
                .with(csrf())
                .contentType("application/json")
                .content(wrongFont))
        .andExpect(status().isBadRequest());
    assertThat(invitations.own(c.id(), host).data().design()).isEqualTo(design);
  }

  InvitationDtos.Body withGift(InvitationDtos.GiftAccounts gift) {
    var b = body("");
    return new InvitationDtos.Body(
        b.template(),
        b.groom(),
        b.bride(),
        b.groomParents(),
        b.brideParents(),
        b.greeting(),
        b.dateTime(),
        b.venue(),
        b.address(),
        b.transport(),
        b.groomPhone(),
        b.bridePhone(),
        b.cover(),
        b.gallery(),
        null,
        gift);
  }

  @Test
  void newspaperContentPersistsAndSurvivesDisabledGiftRedaction() throws Exception {
    var base = body("");
    var n =
        new InvitationDtos.Newspaper(
            "결혼 소식",
            "우리가 결혼합니다",
            "신랑 소개",
            "신부 소개",
            "신랑의 이야기",
            "신부의 이야기",
            "예식 준비",
            "앞으로의 계획",
            "감사합니다",
            java.util.Collections.nCopies(8, "가족"),
            java.util.Collections.nCopies(6, "추억"));
    var gift =
        new InvitationDtos.GiftAccounts(
            false,
            "계좌",
            "",
            List.of(new InvitationDtos.GiftAccount("GROOM", "신랑", "은행", "도윤", "1234567")));
    var input =
        new InvitationDtos.Body(
            "NEWSPAPER",
            base.groom(),
            base.bride(),
            base.groomParents(),
            base.brideParents(),
            base.greeting(),
            base.dateTime(),
            base.venue(),
            base.address(),
            base.transport(),
            base.groomPhone(),
            base.bridePhone(),
            base.cover(),
            base.gallery(),
            null,
            gift,
            n);
    var result =
        mvc.perform(
                post("/api/invitations")
                    .with(user("host"))
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(input)))
            .andExpect(status().isOk())
            .andReturn();
    var c = json.readValue(result.getResponse().getContentAsString(), InvitationDtos.Card.class);
    assertThat(invitations.own(c.id(), host).data().newspaper()).isEqualTo(n);
    invitations.publish(c.id(), host, c.version(), true);
    mvc.perform(get("/api/public-invitations/" + c.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.template").value("NEWSPAPER"))
        .andExpect(jsonPath("$.newspaper.familyLetter").value("감사합니다"))
        .andExpect(jsonPath("$.giftAccounts").isEmpty());
    mvc.perform(
            post("/api/invitations")
                .with(user("host"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(input)
                        .replace("\"신랑 소개\"", "\"" + "가".repeat(201) + "\"")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void giftAccountsPersistAndDisabledAccountsAreRemovedFromPublicResponses() throws Exception {
    var entries =
        List.of(
            new InvitationDtos.GiftAccount("GROOM", "신랑", "테스트은행", "도윤", "123-456-7890"),
            new InvitationDtos.GiftAccount("BRIDE", "어머니", "테스트은행", "최영희", "987 654 3210"));
    var gift = new InvitationDtos.GiftAccounts(true, "마음 전하실 곳", "감사합니다.", entries);
    var result =
        mvc.perform(
                post("/api/invitations")
                    .with(user("host"))
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(withGift(gift))))
            .andExpect(status().isOk())
            .andReturn();
    var c = json.readValue(result.getResponse().getContentAsString(), InvitationDtos.Card.class);
    assertThat(invitations.own(c.id(), host).data().giftAccounts()).isEqualTo(gift);
    mvc.perform(get("/api/public-invitations/" + c.token())).andExpect(status().isNotFound());
    invitations.publish(c.id(), host, c.version(), true);
    mvc.perform(get("/api/public-invitations/" + c.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.giftAccounts.accounts[1].holder").value("최영희"));
    invitations.save(
        c.id(),
        host,
        2,
        withGift(new InvitationDtos.GiftAccounts(false, gift.title(), gift.message(), entries)));
    mvc.perform(get("/api/public-invitations/" + c.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.giftAccounts").isEmpty());
    assertThat(invitations.own(c.id(), host).data().giftAccounts().accounts()).hasSize(2);
    invitations.publish(c.id(), host, 3, false);
    mvc.perform(get("/api/public-invitations/" + c.token())).andExpect(status().isNotFound());
  }

  @Test
  void malformedAndExcessiveGiftAccountsAreRejectedAndOldPayloadsStillLoad() throws Exception {
    for (var gift :
        List.of(
            new InvitationDtos.GiftAccounts(true, "계좌", "", List.of()),
            new InvitationDtos.GiftAccounts(
                true,
                "계좌",
                "",
                List.of(new InvitationDtos.GiftAccount("GROOM", "신랑", "은행", "도윤", "javascript:1"))),
            new InvitationDtos.GiftAccounts(
                true,
                "계좌",
                "",
                List.of(new InvitationDtos.GiftAccount("OTHER", "신랑", "은행", "도윤", "1234567"))),
            new InvitationDtos.GiftAccounts(
                true,
                "계좌",
                "",
                java.util.Collections.nCopies(
                    5, new InvitationDtos.GiftAccount("BRIDE", "신부", "은행", "지은", "1234567")))))
      mvc.perform(
              post("/api/invitations")
                  .with(user("host"))
                  .with(csrf())
                  .contentType("application/json")
                  .content(json.writeValueAsString(withGift(gift))))
          .andExpect(status().isBadRequest());
    var old = invitations.create(host, body(""));
    db.update(
        "UPDATE invitation SET payload=? WHERE id=?",
        json.writeValueAsString(body("")).replace(",\"giftAccounts\":null", ""),
        old.id());
    assertThat(invitations.own(old.id(), host).data().giftAccounts()).isNull();
  }
}
