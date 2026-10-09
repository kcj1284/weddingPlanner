package kr.cheongmo;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.springframework.web.server.ResponseStatusException;
class GuestAccessIntegrationTest extends MeetingIntegrationTest {
    @Autowired GuestAccess links;
    @Test void formattedCodesAreCaseInsensitiveAndLegacyLinksRemainValid() {
        var invitation=links.create(meeting,host,"코드 친구");
        String code=(String)invitation.get("code");
        assertThat(code).matches("[A-HJ-NP-Z2-9]{6}").containsPattern("[A-Z]").containsPattern("[2-9]");
        assertThat(links.preview(code).get("joined")).isEqualTo(false);
        var first=links.enter(code.toLowerCase(Locale.ROOT),"코드 민지");
        assertThat(links.enter(code.substring(0,3)+" "+code.substring(3),null).id()).isEqualTo(first.id());
        assertThatThrownBy(()->links.preview("000000")).isInstanceOf(ResponseStatusException.class);
        String oldCode="ABCD-EFGH-JKLM-NPQR-STUV";
        db.update("UPDATE guest_link SET token_hash=? WHERE id=?",GuestAccess.hash(oldCode),invitation.get("id"));
        assertThat(links.enter(oldCode.toLowerCase(Locale.ROOT),null).id()).isEqualTo(first.id());
        byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);
        String legacy=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        db.update("UPDATE guest_link SET token_hash=? WHERE id=?",GuestAccess.hash(legacy),invitation.get("id"));
        assertThat(links.enter(legacy,null).id()).isEqualTo(first.id());
        links.revoke(meeting,host,(String)invitation.get("id"));
        assertThatThrownBy(()->links.preview(legacy)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void uniqueLinksCreateIndependentGuestsAndReentryKeepsVoteIdentity() {
        var a=links.create(meeting,host,"친구 A"); var b=links.create(meeting,host,"친구 B");
        assertThat(a.get("token")).isNotEqualTo(b.get("token"));
        var first=links.enter((String)a.get("token"),"민지");
        var option=service.addOption(meeting,host,new Meetings.OptionInput("PLACE","카페","","",""));
        service.vote(meeting,first.id(),List.of(option));
        var again=links.enter((String)a.get("token"),"다른 이름");
        var second=links.enter((String)b.get("token"),"지수");
        assertThat(again.id()).isEqualTo(first.id());assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(service.detail(meeting,again.id()).get("myVotes")).isEqualTo(List.of(option));
        assertThat(service.detail(meeting,first.id()).get("role")).isEqualTo("GUEST");
        assertThatThrownBy(()->service.create(first.id(),new Meetings.Create("불가","","서울",3))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->links.create(meeting,first.id(),"불가")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.join((String)service.detail(meeting,host).get("inviteToken"),first.id())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->accounts.loadUserByUsername(first.username())).isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
        links.revoke(meeting,host,(String)a.get("id"));
        assertThatThrownBy(()->links.enter((String)a.get("token"),"민지")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.detail(meeting,first.id())).isInstanceOf(ResponseStatusException.class);
        assertThat(service.detail(meeting,second.id()).get("memberCount")).isEqualTo(2);
    }
    @Test void expiredAndInvalidLinksCannotBeUsed() {
        var link=links.create(meeting,host,"친구");
        db.update("UPDATE guest_link SET expires_at=TIMESTAMP '2020-01-01 00:00:00' WHERE id=?",link.get("id"));
        assertThatThrownBy(()->links.preview((String)link.get("token"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->links.enter("unknown","민지")).isInstanceOf(ResponseStatusException.class);
    }
    @Test void socialIdentityUsesProviderSubjectAndDoesNotMergeDifferentProviders() {
        var a=accounts.social("kakao","subject-1","호스트");
        assertThat(accounts.social("kakao","subject-1","이름 변경").id()).isEqualTo(a.id());
        assertThat(accounts.social("google","subject-1","호스트").id()).isNotEqualTo(a.id());
        assertThat(a.guest()).isFalse();
    }
}
