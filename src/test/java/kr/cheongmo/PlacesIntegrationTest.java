package kr.cheongmo;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;
class PlacesIntegrationTest extends MeetingIntegrationTest {
    @Autowired Places places;
    @Test void missingKeyIsExplicitAndGuestCannotSearchOrSetReservation() {
        assertThat(places.search(meeting,host,"성수","CE7").get("configured")).isEqualTo(false);
        service.join((String)service.detail(meeting,host).get("inviteToken"),guest);
        assertThatThrownBy(()->places.search(meeting,guest,"성수","CE7")).isInstanceOf(ResponseStatusException.class);
        String place=service.addOption(meeting,host,new Meetings.OptionInput("PLACE","예시 카페","","",""));
        assertThatThrownBy(()->service.reservation(meeting,guest,place,"BOOKED","예약")).isInstanceOf(ResponseStatusException.class);
        service.reservation(meeting,host,place,"BOOKED","전화로 6명 예약 완료");
        assertThat(db.queryForObject("SELECT reservation FROM poll_option WHERE id=?",String.class,place)).isEqualTo("BOOKED");
    }
}
