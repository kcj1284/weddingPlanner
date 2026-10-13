package kr.cheongmo;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import kr.cheongmo.place.PlaceDiscoveryRepository;
import kr.cheongmo.place.PlaceDiscoveryService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
class PlaceDiscoveryIntegrationTest {
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  HttpServer upstream;
  PlaceDiscoveryService discovery;
  AtomicInteger localCalls = new AtomicInteger(), blogCalls = new AtomicInteger();
  CountDownLatch release = new CountDownLatch(1);

  @BeforeEach
  void setup() throws Exception {
    if (!db.queryForObject("SELECT current_database()", String.class).endsWith("_test"))
      throw new IllegalStateException("Test DB required");
    db.update("DELETE FROM place_discovery_cache");
    upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    upstream.createContext(
        "/v1/search/local.json",
        exchange -> {
          localCalls.incrementAndGet();
          String body =
              "{\"items\":[{\"title\":\"A식당\",\"category\":\"음식점\",\"address\":\"서울"
                  + " 성동구\"},{\"title\":\"B식당\",\"category\":\"음식점\",\"address\":\"서울 성동구\"}]}";
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    upstream.createContext(
        "/v1/search/blog.json",
        exchange -> {
          blogCalls.incrementAndGet();
          try {
            release.await(2, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          String body =
              "{\"items\":[{\"title\":\"<b>B식당</b> 청첩장 모임 후기\",\"description\":\"청모"
                  + " 모임\",\"link\":\"https://blog.naver.com/example/1\",\"postdate\":\"20261012\"},{\"title\":\"다른"
                  + " 식당 청모\",\"description\":\"관련"
                  + " 없음\",\"link\":\"https://blog.naver.com/example/2\"}]}";
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    upstream.start();
    String url = "http://127.0.0.1:" + upstream.getAddress().getPort();
    discovery =
        new PlaceDiscoveryService(
            new PlaceDiscoveryRepository(db, json), json, "", "test-id", "test-secret", url, url);
  }

  @AfterEach
  void close() {
    release.countDown();
    discovery.close();
    upstream.stop(0);
  }

  @SuppressWarnings("unchecked")
  @Test
  void listReturnsBeforeReviewsThenCachesAndPromotesRelevantPosts() throws Exception {
    var first = discovery.search("서울 성수", "FD6");
    assertThat(first.get("pending")).isEqualTo(true);
    assertThat(((List<Map<String, Object>>) first.get("places")).getFirst().get("name"))
        .isEqualTo("A식당");
    discovery.search("서울 성수", "FD6");
    assertThat(localCalls.get()).isEqualTo(1);
    release.countDown();
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    Map<String, Object> done;
    do {
      done = discovery.reviews("서울 성수", "FD6");
      if (!Boolean.TRUE.equals(done.get("pending"))) break;
      Thread.sleep(30);
    } while (System.nanoTime() < deadline);
    assertThat(done.get("pending")).isEqualTo(false);
    var places = (List<Map<String, Object>>) done.get("places");
    assertThat(places.getFirst().get("name")).isEqualTo("B식당");
    var blogs = (List<Map<String, Object>>) places.getFirst().get("blogs");
    assertThat(blogs).hasSize(1);
    assertThat(blogs.getFirst().get("title")).isEqualTo("B식당 청첩장 모임 후기");
    assertThat((List<?>) places.get(1).get("blogs")).isEmpty();
    int calls = blogCalls.get();
    discovery.search("서울 성수", "FD6");
    assertThat(blogCalls.get()).isEqualTo(calls);
    assertThat(calls).isEqualTo(6);
  }

  @Test
  void expiredCacheReturnsOldListWithoutWaitingForRefresh() {
    discovery.save(
        "SEARCH",
        "서울 성수",
        "FD6",
        Map.of(
            "configured",
            true,
            "places",
            List.of(),
            "source",
            "naver",
            "order",
            "review",
            "message",
            "cached"),
        Duration.ofSeconds(-1));
    var result = discovery.search("서울 성수", "FD6");
    assertThat(result.get("message")).isEqualTo("cached");
    assertThat(result.get("pending")).isEqualTo(true);
    assertThat(result.get("searchRefreshing")).isEqualTo(true);
  }

  @Test
  void sameNameBranchesUseSeparateReviewIdentities() {
    String a = discovery.reviewQuery(PlaceDiscoveryService.place("같은 식당", "서울 강남구 가로수길 1", "", ""));
    String b = discovery.reviewQuery(PlaceDiscoveryService.place("같은 식당", "서울 강남구 테헤란로 2", "", ""));
    assertThat(a).isNotEqualTo(b);
    assertThat(discovery.key("REVIEW", a, "FD6")).isNotEqualTo(discovery.key("REVIEW", b, "FD6"));
  }

  @Test
  void missingSearchCredentialsDoesNotClaimPopularity() {
    discovery.close();
    String url = "http://127.0.0.1:" + upstream.getAddress().getPort();
    discovery =
        new PlaceDiscoveryService(
            new PlaceDiscoveryRepository(db, json), json, "", "", "", url, url);
    assertThat(discovery.search("서울 성수", "FD6").get("reviewStatus")).isEqualTo("unavailable");
    assertThat(localCalls.get()).isZero();
    assertThat(blogCalls.get()).isZero();
  }

  @Test
  void reviewFailureKeepsPreviousPostsAndBacksOff() {
    String query = "B식당|서울 성동구";
    discovery.save(
        "REVIEW",
        query,
        "FD6",
        Map.of("status", "ready", "blogs", List.of(Map.of("title", "기존 글"))),
        Duration.ofSeconds(-1));
    upstream.removeContext("/v1/search/blog.json");
    upstream.createContext(
        "/v1/search/blog.json",
        exchange -> {
          exchange.sendResponseHeaders(503, -1);
          exchange.close();
        });
    discovery.loadReviews(query, "FD6");
    var cached = discovery.cached("REVIEW", query, "FD6");
    assertThat(cached.fresh()).isTrue();
    assertThat(cached.value().get("status")).isEqualTo("failed");
    assertThat((List<?>) cached.value().get("blogs")).hasSize(1);
  }
}
