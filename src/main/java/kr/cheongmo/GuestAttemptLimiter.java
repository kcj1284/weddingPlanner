package kr.cheongmo;
import java.util.*;
import java.util.function.LongSupplier;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;

/** Bounds online guessing of short invite codes on this server instance. */
@Component
class GuestAttemptLimiter {
    private record Window(long started,int count) {}
    private final Map<String,Window> windows=new HashMap<>();
    private final LongSupplier clock;
    GuestAttemptLimiter(){this(System::currentTimeMillis);}
    GuestAttemptLimiter(LongSupplier clock){this.clock=clock;}
    synchronized void check(String address) {
        long now=clock.getAsLong();
        var window=windows.get(address);
        if(window==null||now-window.started()>=60_000) {
            if(windows.size()>=2048){windows.entrySet().removeIf(e->now-e.getValue().started()>=60_000);}
            if(window==null&&windows.size()>=2048)throw limited();
            window=new Window(now,0);
        }
        if(window.count()>=30)throw limited();
        windows.put(address,new Window(window.started(),window.count()+1));
    }
    private static org.springframework.web.server.ResponseStatusException limited(){return Meetings.error(HttpStatus.TOO_MANY_REQUESTS,"초대코드 확인을 여러 번 시도했어요. 1분 후 다시 시도해 주세요.");}
}
