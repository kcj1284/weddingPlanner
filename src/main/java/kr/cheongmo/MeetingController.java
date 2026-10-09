package kr.cheongmo;
import java.util.*;
import java.security.Principal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api")
class MeetingController {
    private final Accounts accounts; private final Meetings meetings;
    MeetingController(Accounts a, Meetings m) {accounts=a;meetings=m;}
    String user(Principal p) { return accounts.account(p.getName()).id(); }
    @GetMapping("/meetings") List<Map<String,Object>> list(Principal p) {return meetings.list(user(p));}
    @PostMapping("/meetings") Map<String,String> create(Principal p,@Valid @RequestBody Meetings.Create in) {return Map.of("id",meetings.create(user(p),in));}
    @PostMapping("/join/{token}") Map<String,String> join(Principal p,@PathVariable String token) {return Map.of("id",meetings.join(token,user(p)));}
    @GetMapping("/meetings/{id}") Map<String,Object> detail(Principal p,@PathVariable String id) {return meetings.detail(id,user(p));}
    @PostMapping("/meetings/{id}/options") Map<String,String> option(Principal p,@PathVariable String id,@Valid @RequestBody Meetings.OptionInput in) {return Map.of("id",meetings.addOption(id,user(p),in));}
    record VoteInput(@NotNull @Size(max=60) List<@NotBlank String> optionIds) {}
    @PutMapping("/meetings/{id}/votes") Map<String,Boolean> vote(Principal p,@PathVariable String id,@Valid @RequestBody VoteInput in) {meetings.vote(id,user(p),in.optionIds());return Map.of("saved",true);}
    record Confirmation(@NotBlank String timeId,@NotBlank String placeId) {}
    @PostMapping("/meetings/{id}/confirm") Map<String,Boolean> confirm(Principal p,@PathVariable String id,@Valid @RequestBody Confirmation in) {meetings.confirm(id,user(p),in.timeId(),in.placeId());return Map.of("saved",true);}
    @PostMapping("/meetings/{id}/reopen") Map<String,Boolean> reopen(Principal p,@PathVariable String id) {meetings.reopen(id,user(p));return Map.of("saved",true);}
    record Reservation(@NotNull @Pattern(regexp="UNKNOWN|AVAILABLE|BOOKED") String status,@Size(max=300) String note) {}
    @PutMapping("/meetings/{id}/options/{option}/reservation") Map<String,Boolean> reservation(Principal p,@PathVariable String id,@PathVariable String option,@Valid @RequestBody Reservation in) {meetings.reservation(id,user(p),option,in.status(),in.note());return Map.of("saved",true);}
}
