package kr.cheongmo;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import kr.cheongmo.guest.GroupInviteService;
import kr.cheongmo.guest.GuestAccessService;
import kr.cheongmo.meeting.dto.MeetingDtos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

class GroupInviteIntegrationTest extends MeetingIntegrationTest {
  @Autowired GroupInviteService groups;
  @Autowired GuestAccessService personal;

  @Test
  void sharedLinkCreatesIndependentPeopleAndPersonalReentryPreservesVotes() {
    var link = groups.create(meeting, host);
    String token = (String) link.get("token");
    var a = groups.enter(token, "민지", null);
    var b = groups.enter(token, "지수", null);
    assertThat(a.account().id()).isNotEqualTo(b.account().id());
    assertThat(a.personalToken()).isNotEqualTo(b.personalToken());
    String option =
        service.addOption(meeting, host, new MeetingDtos.OptionInput("PLACE", "카페", "", "", ""));
    service.vote(meeting, a.account().id(), List.of(option));
    assertThat(service.detail(meeting, b.account().id()).myVotes()).isEqualTo(List.of());
    assertThat(personal.enter(a.personalToken(), null).id()).isEqualTo(a.account().id());
    var reentry = groups.enter(token, "민지", a.account());
    assertThat(reentry.account().id()).isEqualTo(a.account().id());
    assertThat(reentry.personalToken()).isEqualTo(a.personalToken());
    groups.revoke(meeting, host, (String) link.get("id"));
    assertThatThrownBy(() -> groups.enter(token, "새 친구", null))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(personal.enter(a.personalToken(), null).id()).isEqualTo(a.account().id());
    assertThat(service.detail(meeting, a.account().id()).myVotes()).isEqualTo(List.of(option));
  }

  @Test
  void groupLinksRequireHostAndExpiredLinksCannotJoin() {
    assertThatThrownBy(() -> groups.create(meeting, guest))
        .isInstanceOf(ResponseStatusException.class);
    var link = groups.create(meeting, host);
    String token = (String) link.get("token");
    assertThatThrownBy(() -> groups.enter(token, " ", null))
        .isInstanceOf(ResponseStatusException.class);
    db.update(
        "UPDATE group_invite SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE id=?",
        link.get("id"));
    assertThat(groups.list(meeting, host).getFirst().get("expired")).isEqualTo(true);
    assertThatThrownBy(() -> groups.preview(token)).isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void legacyPersonalLinkCanBeRecoveredThroughLiveGroupWithoutNewIdentity() {
    var link = groups.create(meeting, host);
    String token = (String) link.get("token");
    var joined = groups.enter(token, "민지", null);
    db.update(
        "UPDATE guest_link SET code_cipher=NULL WHERE guest_user_id=?", joined.account().id());
    var recovered = groups.enter(token, "민지", joined.account());
    assertThat(recovered.account().id()).isEqualTo(joined.account().id());
    assertThat(recovered.personalToken()).isNotBlank();
    assertThat(personal.enter(recovered.personalToken(), null).id())
        .isEqualTo(joined.account().id());
    assertThat(service.detail(meeting, joined.account().id()).memberCount()).isEqualTo(2);
  }
}
