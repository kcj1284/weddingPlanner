package kr.cheongmo;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import kr.cheongmo.guest.GuestAccessService;
import kr.cheongmo.guest.InviteCodeCipher;
import kr.cheongmo.meeting.dto.MeetingDtos;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

class GuestAccessIntegrationTest extends MeetingIntegrationTest {
  @Autowired GuestAccessService links;
  @Autowired InviteCodeCipher codeCipher;

  @Test
  void encryptedCodeCanBeRevealedOnlyByItsHost() {
    var invitation = links.create(meeting, host, "민지");
    String id = (String) invitation.get("id"), code = (String) invitation.get("code");
    assertThat(links.reveal(meeting, host, id).get("code")).isEqualTo(code);
    String encrypted =
        db.queryForObject("SELECT code_cipher FROM guest_link WHERE id=?", String.class, id);
    assertThat(encrypted).doesNotContain(code);
    assertThat(codeCipher.decrypt(encrypted, id)).isEqualTo(code);
    assertThatThrownBy(() -> codeCipher.decrypt(encrypted, "another-id"))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> links.reveal(meeting, outsider, id))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> links.reissue(meeting, outsider, id))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void legacyReissuePreservesGuestAndVotesButInvalidatesOldCode() {
    var invitation = links.create(meeting, host, "민지");
    String id = (String) invitation.get("id"), old = (String) invitation.get("code");
    var person = links.enter(old, "민지");
    String option =
        service.addOption(meeting, host, new MeetingDtos.OptionInput("PLACE", "카페", "", "", ""));
    service.vote(meeting, person.id(), List.of(option));
    db.update("UPDATE guest_link SET code_cipher=NULL WHERE id=?", id);
    assertThat(links.reveal(meeting, host, id).get("recoverable")).isEqualTo(false);
    var renewed = links.reissue(meeting, host, id);
    String code = (String) renewed.get("code");
    assertThat(code).matches("[A-HJ-NP-Z2-9]{6}").isNotEqualTo(old);
    assertThatThrownBy(() -> links.preview(old)).isInstanceOf(ResponseStatusException.class);
    assertThat(links.enter(code, null).id()).isEqualTo(person.id());
    assertThat(service.detail(meeting, person.id()).myVotes()).isEqualTo(List.of(option));
    assertThat(links.reveal(meeting, host, id).get("code")).isEqualTo(code);
    links.revoke(meeting, host, id);
    assertThatThrownBy(() -> links.reissue(meeting, host, id))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void expiredCodeRequiresRenewal() {
    var invitation = links.create(meeting, host, "친구");
    String id = (String) invitation.get("id");
    db.update(
        "UPDATE guest_link SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE id=?", id);
    assertThat(links.reveal(meeting, host, id).get("expired")).isEqualTo(true);
    assertThat(links.list(meeting, host).getFirst().get("expired")).isEqualTo(true);
    var renewed = links.reissue(meeting, host, id);
    assertThat(links.preview((String) renewed.get("code")).get("joined")).isEqualTo(false);
  }

  @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

  @Test
  void codeCollisionRetriesWithoutAbortingPostgresTransaction() {
    var existing = links.create(meeting, host, "먼저 초대");
    db.update(
        "UPDATE guest_link SET token_hash=? WHERE id=?",
        GuestAccessService.hash("A2AAAA"),
        existing.get("id"));
    var controlled =
        org.mockito.Mockito.spy(
            new GuestAccessService(
                new kr.cheongmo.guest.GuestAccessRepository(db), service, accounts, codeCipher));
    org.mockito.Mockito.doReturn("A2AAAA", "B3BBBB").when(controlled).candidateCode();
    var result =
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
            .execute(
                status -> {
                  var invitation = controlled.create(meeting, host, "두 번째 초대");
                  assertThat(db.queryForObject("SELECT COUNT(*) FROM guest_link", Integer.class))
                      .isEqualTo(2);
                  return invitation;
                });
    assertThat(result.get("code")).isEqualTo("B3BBBB");
    assertThat(links.preview("B3BBBB").get("joined")).isEqualTo(false);
  }

  @Test
  void formattedCodesAreCaseInsensitiveAndLegacyLinksRemainValid() {
    var invitation = links.create(meeting, host, "코드 친구");
    String code = (String) invitation.get("code");
    assertThat(code).matches("[A-HJ-NP-Z2-9]{6}").containsPattern("[A-Z]").containsPattern("[2-9]");
    assertThat(links.preview(code).get("joined")).isEqualTo(false);
    var first = links.enter(code.toLowerCase(Locale.ROOT), "코드 민지");
    assertThat(links.enter(code.substring(0, 3) + " " + code.substring(3), null).id())
        .isEqualTo(first.id());
    assertThatThrownBy(() -> links.preview("000000")).isInstanceOf(ResponseStatusException.class);
    String oldCode = "ABCD-EFGH-JKLM-NPQR-STUV";
    db.update(
        "UPDATE guest_link SET token_hash=? WHERE id=?",
        GuestAccessService.hash(oldCode),
        invitation.get("id"));
    assertThat(links.enter(oldCode.toLowerCase(Locale.ROOT), null).id()).isEqualTo(first.id());
    byte[] bytes = new byte[32];
    new java.security.SecureRandom().nextBytes(bytes);
    String legacy = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    db.update(
        "UPDATE guest_link SET token_hash=? WHERE id=?",
        GuestAccessService.hash(legacy),
        invitation.get("id"));
    assertThat(links.enter(legacy, null).id()).isEqualTo(first.id());
    links.revoke(meeting, host, (String) invitation.get("id"));
    assertThatThrownBy(() -> links.preview(legacy)).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void uniqueLinksCreateIndependentGuestsAndReentryKeepsVoteIdentity() {
    var a = links.create(meeting, host, "친구 A");
    var b = links.create(meeting, host, "친구 B");
    assertThat(a.get("token")).isNotEqualTo(b.get("token"));
    var first = links.enter((String) a.get("token"), "민지");
    var option =
        service.addOption(meeting, host, new MeetingDtos.OptionInput("PLACE", "카페", "", "", ""));
    service.vote(meeting, first.id(), List.of(option));
    var again = links.enter((String) a.get("token"), "다른 이름");
    var second = links.enter((String) b.get("token"), "지수");
    assertThat(again.id()).isEqualTo(first.id());
    assertThat(second.id()).isNotEqualTo(first.id());
    assertThat(service.detail(meeting, again.id()).myVotes()).isEqualTo(List.of(option));
    assertThat(service.detail(meeting, first.id()).role()).isEqualTo("GUEST");
    assertThatThrownBy(() -> service.create(first.id(), new MeetingDtos.Create("불가", "", "서울", 3)))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> links.create(meeting, first.id(), "불가"))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(
            () -> service.join((String) service.detail(meeting, host).inviteToken(), first.id()))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> accounts.loadUserByUsername(first.username()))
        .isInstanceOf(
            org.springframework.security.core.userdetails.UsernameNotFoundException.class);
    links.revoke(meeting, host, (String) a.get("id"));
    assertThatThrownBy(() -> links.enter((String) a.get("token"), "민지"))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> service.detail(meeting, first.id()))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(service.detail(meeting, second.id()).memberCount()).isEqualTo(2);
  }

  @Test
  void expiredAndInvalidLinksCannotBeUsed() {
    var link = links.create(meeting, host, "친구");
    db.update(
        "UPDATE guest_link SET expires_at=TIMESTAMP '2020-01-01 00:00:00' WHERE id=?",
        link.get("id"));
    assertThatThrownBy(() -> links.preview((String) link.get("token")))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> links.enter("unknown", "민지"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void socialIdentityUsesProviderSubjectAndDoesNotMergeDifferentProviders() {
    var a = accounts.social("kakao", "subject-1", "호스트");
    assertThat(accounts.social("kakao", "subject-1", "이름 변경").id()).isEqualTo(a.id());
    assertThat(accounts.social("google", "subject-1", "호스트").id()).isNotEqualTo(a.id());
    assertThat(a.guest()).isFalse();
  }
}
