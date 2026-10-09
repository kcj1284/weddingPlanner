package kr.cheongmo;
import java.util.*;
import java.security.Principal;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
@Service
class Places {
    private final Meetings meetings; private final String key; private final RestClient client;
    Places(Meetings meetings,@Value("${cheongmo.kakao-key:}") String key) {
        this.meetings=meetings;this.key=key;
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));client=RestClient.builder().requestFactory(factory).baseUrl("https://dapi.kakao.com").build();
    }
    @SuppressWarnings("unchecked")
    Map<String,Object> search(String id,String user,String q,String type) {
        meetings.requireHost(id,user,false);
        if(q==null||q.isBlank()||q.length()>100||!List.of("FD6","CE7").contains(type)) throw Meetings.error(HttpStatus.BAD_REQUEST,"검색어와 식당·카페 종류를 확인해 주세요.");
        if(key.isBlank()) return Map.of("configured",false,"message","장소 검색 연결을 준비 중이에요. 지도에서 찾아보거나 장소를 직접 추가해 주세요.","places",List.of());
        try {
            Map<String,Object> result=client.get().uri(u->u.path("/v2/local/search/keyword.json").queryParam("query",q).queryParam("category_group_code",type).queryParam("size",15).build())
                .header("Authorization","KakaoAK "+key).retrieve().body(Map.class);
            List<Map<String,Object>> docs=(List<Map<String,Object>>)result.getOrDefault("documents",List.of());
            List<Map<String,String>> places=docs.stream().map(d->Map.of("name",Objects.toString(d.get("place_name"),""),"address",Objects.toString(d.get("road_address_name"),"").isBlank()?Objects.toString(d.get("address_name"),""):Objects.toString(d.get("road_address_name"),""),"phone",Objects.toString(d.get("phone"),""),"url",Objects.toString(d.get("place_url"),""))).toList();
            return Map.of("configured",true,"message","예약 가능 여부는 인원과 날짜를 알려주고 직접 확인해 주세요.","places",places);
        } catch(RuntimeException ex) { return Map.of("configured",true,"message","장소 검색에 연결하지 못했어요. 잠시 후 다시 시도하거나 직접 추가해 주세요.","places",List.of()); }
    }
}
@RestController
class PlacesController {
    private final Places places; private final Accounts accounts;
    PlacesController(Places p,Accounts a){places=p;accounts=a;}
    @GetMapping("/api/meetings/{id}/places") Map<String,Object> search(Principal p,@PathVariable String id,@RequestParam String q,@RequestParam(defaultValue="FD6") String type) {return places.search(id,accounts.account(p.getName()).id(),q,type);}
}
