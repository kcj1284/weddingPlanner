package kr.cheongmo;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.List;
@Component @Profile("local")
class DemoData implements CommandLineRunner {
    private final Accounts accounts; private final Meetings meetings; private final JdbcTemplate db;
    DemoData(Accounts a,Meetings m,JdbcTemplate d){accounts=a;meetings=m;db=d;}
    @Override public void run(String... args) {
        if(db.queryForObject("SELECT COUNT(*) FROM app_user WHERE username='demo_host'",Integer.class)>0) return;
        String host=accounts.register(new Accounts.Registration("demo_host","지은","cheongmo-demo")).id();
        String guest=accounts.register(new Accounts.Registration("demo_guest","수민","cheongmo-demo")).id();
        String id=meetings.create(host,new Meetings.Create("우리, 청첩장 나오는 날","좋은 소식을 가장 먼저 전하고 싶은 사람들. 맛있는 식사와 함께 만나요!","서울 성수",6));
        meetings.join((String)meetings.detail(id,host).get("inviteToken"),guest);
        LocalDate day=LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(14);
        String t1=meetings.addOption(id,host,new Meetings.OptionInput("TIME",day.atTime(18,30).toString(),"퇴근 후 저녁","",""));
        String t2=meetings.addOption(id,host,new Meetings.OptionInput("TIME",day.plusDays(1).atTime(12,0).toString(),"여유로운 점심","",""));
        meetings.addOption(id,host,new Meetings.OptionInput("TIME",day.plusDays(2).atTime(17,0).toString(),"조금 이른 저녁","",""));
        String place=meetings.addOption(id,host,new Meetings.OptionInput("PLACE","예시 · 따뜻한 이탈리안 식당","성수역 근처 / 장소 입력 예시이며 실제 업체가 아닙니다.","",""));
        meetings.addOption(id,host,new Meetings.OptionInput("PLACE","예시 · 조용한 브런치 카페","서울숲 근처 / 실제 예약 정보는 직접 확인해 주세요.","",""));
        meetings.vote(id,guest,List.of(t1,t2,place));
    }
}
