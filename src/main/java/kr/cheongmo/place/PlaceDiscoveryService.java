package kr.cheongmo.place;

import jakarta.annotation.PreDestroy;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import kr.cheongmo.place.dto.PlaceDtos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Service
@EnableScheduling
public class PlaceDiscoveryService {
  private final PlaceDiscoveryRepository repository;
  private final ObjectMapper objectMapper;
  private final String kakaoKey, naverId, naverSecret;
  private final RestClient kakao, naver;
  private final ThreadPoolExecutor workers =
      new ThreadPoolExecutor(
          2,
          2,
          30,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(20),
          r -> {
            Thread t = new Thread(r, "place-reviews");
            t.setDaemon(true);
            return t;
          },
          new ThreadPoolExecutor.AbortPolicy());
  private final Set<String> running = ConcurrentHashMap.newKeySet();

  public PlaceDiscoveryService(
      PlaceDiscoveryRepository repository,
      ObjectMapper objectMapper,
      @Value("${cheongmo.kakao-key:}") String kakaoKey,
      @Value("${cheongmo.naver-search-id:}") String naverId,
      @Value("${cheongmo.naver-search-secret:}") String naverSecret,
      @Value("${cheongmo.kakao-api-url:https://dapi.kakao.com}") String kakaoUrl,
      @Value("${cheongmo.naver-search-url:https://openapi.naver.com}") String naverUrl) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.kakaoKey = kakaoKey;
    this.naverId = naverId;
    this.naverSecret = naverSecret;
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    factory.setReadTimeout(Duration.ofSeconds(3));
    kakao = RestClient.builder().requestFactory(factory).baseUrl(kakaoUrl).build();
    naver = RestClient.builder().requestFactory(factory).baseUrl(naverUrl).build();
  }

  public boolean naverConfigured() {
    return !naverId.isBlank() && !naverSecret.isBlank();
  }

  public String key(String kind, String query, String type) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      (kind + "|" + type + "|" + query.trim().replaceAll("\\s+", " "))
                          .getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @SuppressWarnings("unchecked")
  public PlaceDtos.Cached cached(String kind, String query, String type) {
    String key = key(kind, query, type);
    var rows = repository.findCache(key);
    if (rows.isEmpty()) return null;
    repository.touchCache(key);
    return rows.getFirst();
  }

  public void save(
      String kind, String query, String type, Map<String, Object> value, Duration ttl) {
    repository.saveCache(
        key(kind, query, type),
        kind,
        query,
        type,
        objectMapper.writeValueAsString(value),
        OffsetDateTime.now().plus(ttl));
  }

  public void queue(String key, Runnable task) {
    if (!running.add(key)) return;
    try {
      workers.execute(
          () -> {
            try {
              task.run();
            } catch (RuntimeException ignored) {
            } finally {
              running.remove(key);
            }
          });
    } catch (RejectedExecutionException e) {
      running.remove(key);
    }
  }

  public Map<String, Object> search(String q, String type) {
    PlaceDtos.Cached cached = cached("SEARCH", q, type);
    if (cached != null) {
      boolean refresh =
          !cached.fresh()
              || (naverConfigured() && !Boolean.TRUE.equals(cached.value().get("naverAttempted")));
      if (refresh) queue(key("SEARCH", q, type), () -> loadSearch(q, type));
      var result = enrich(cached.value(), q, type);
      result.put("searchRefreshing", refresh);
      if (refresh) result.put("pending", true);
      return result;
    }
    return enrich(loadSearch(q, type), q, type);
  }

  public Map<String, Object> reviews(String q, String type) {
    PlaceDtos.Cached cached = cached("SEARCH", q, type);
    if (cached == null)
      return Map.of("places", List.of(), "pending", false, "reviewStatus", "unavailable");
    return enrich(cached.value(), q, type);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> loadSearch(String q, String type) {
    Map<String, Object> result;
    try {
      if (naverConfigured()) {
        var response =
            naver
                .get()
                .uri(
                    u ->
                        u.path("/v1/search/local.json")
                            .queryParam("query", q + " " + (type.equals("FD6") ? "식당" : "카페"))
                            .queryParam("display", 5)
                            .queryParam("sort", "comment")
                            .build())
                .header("X-Naver-Client-Id", naverId)
                .header("X-Naver-Client-Secret", naverSecret)
                .retrieve()
                .body(Map.class);
        var items = (List<Map<String, Object>>) response.getOrDefault("items", List.of());
        var places = new ArrayList<Map<String, Object>>();
        for (var item : items) {
          String category = text(item.get("category"));
          if (type.equals("CE7") ? !category.contains("카페") : !category.contains("음식점")) continue;
          String name = plain(item.get("title")), address = text(item.get("roadAddress"));
          if (address.isBlank()) address = text(item.get("address"));
          places.add(
              place(
                  name,
                  address,
                  text(item.get("telephone")),
                  "https://map.naver.com/p/search/"
                      + java.net.URLEncoder.encode(address + " " + name, StandardCharsets.UTF_8)));
        }
        result =
            Map.of(
                "configured",
                true,
                "places",
                places,
                "source",
                "naver",
                "order",
                "review",
                "naverAttempted",
                true,
                "message",
                "네이버 카페·블로그 리뷰 많은 순 후보입니다. 예약 가능 여부는 직접 확인해 주세요.");
        save("SEARCH", q, type, result, Duration.ofHours(24));
        return result;
      }
    } catch (RuntimeException ignored) {
      /* Keep the existing search available when Naver fails. */
    }
    if (kakaoKey.isBlank())
      return Map.of(
          "configured",
          false,
          "places",
          List.of(),
          "source",
          "none",
          "order",
          "accuracy",
          "message",
          "장소 검색 연결을 준비 중이에요. 지도에서 찾아보거나 장소를 직접 추가해 주세요.");
    try {
      var response =
          kakao
              .get()
              .uri(
                  u ->
                      u.path("/v2/local/search/keyword.json")
                          .queryParam("query", q)
                          .queryParam("category_group_code", type)
                          .queryParam("size", 15)
                          .build())
              .header("Authorization", "KakaoAK " + kakaoKey)
              .retrieve()
              .body(Map.class);
      var items = (List<Map<String, Object>>) response.getOrDefault("documents", List.of());
      var places = new ArrayList<Map<String, Object>>();
      for (var item : items) {
        String address = text(item.get("road_address_name"));
        if (address.isBlank()) address = text(item.get("address_name"));
        places.add(
            place(
                text(item.get("place_name")),
                address,
                text(item.get("phone")),
                text(item.get("place_url"))));
      }
      result =
          Map.of(
              "configured",
              true,
              "places",
              places,
              "source",
              "kakao",
              "order",
              "accuracy",
              "naverAttempted",
              naverConfigured(),
              "message",
              naverConfigured()
                  ? "리뷰순 연결이 원활하지 않아 카카오 정확도순으로 표시합니다. 예약은 직접 확인해 주세요."
                  : "카카오 정확도순 결과입니다. 리뷰순·청모 후기 검색은 네이버 검색 API 연결 후 제공됩니다.");
      save("SEARCH", q, type, result, Duration.ofHours(naverConfigured() ? 1 : 24));
      return result;
    } catch (RuntimeException e) {
      return Map.of(
          "configured",
          true,
          "places",
          List.of(),
          "source",
          "kakao",
          "order",
          "accuracy",
          "message",
          "장소 검색에 연결하지 못했어요. 잠시 후 다시 시도하거나 직접 추가해 주세요.");
    }
  }

  public static Map<String, Object> place(String name, String address, String phone, String url) {
    return Map.of("name", name, "address", address, "phone", phone, "url", url);
  }

  public static String text(Object value) {
    return Objects.toString(value, "");
  }

  public static String plain(Object value) {
    return text(value)
        .replaceAll("<[^>]*>", "")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">");
  }

  public String reviewQuery(Map<String, Object> place) {
    return text(place.get("name")) + "|" + text(place.get("address")).trim();
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> enrich(Map<String, Object> base, String q, String type) {
    var result = new LinkedHashMap<>(base);
    var places = new ArrayList<Map<String, Object>>();
    boolean pending = false;
    for (var place : (List<Map<String, Object>>) base.getOrDefault("places", List.of())) {
      var item = new LinkedHashMap<>(place);
      String query = reviewQuery(place);
      if (!naverConfigured()) {
        item.put("reviewStatus", "unavailable");
        item.put("blogs", List.of());
      } else {
        PlaceDtos.Cached review = cached("REVIEW", query, type);
        if (review == null || !review.fresh()) {
          queue(key("REVIEW", query, type), () -> loadReviews(query, type));
          pending = true;
        }
        item.put("reviewStatus", review == null ? "pending" : text(review.value().get("status")));
        item.put(
            "blogs", review == null ? List.of() : review.value().getOrDefault("blogs", List.of()));
      }
      places.add(item);
    }
    // Stable sort: keep provider review order within each group; do not invent review counts.
    places.sort(Comparator.comparingInt(p -> ((List<?>) p.get("blogs")).isEmpty() ? 1 : 0));
    boolean refreshing = running.contains(key("SEARCH", q, type));
    result.put("places", places);
    result.put("pending", pending || refreshing);
    result.put("searchRefreshing", refreshing);
    result.put("reviewStatus", naverConfigured() ? "enabled" : "unavailable");
    return result;
  }

  @SuppressWarnings("unchecked")
  public void loadReviews(String query, String type) {
    var links = new LinkedHashMap<String, Map<String, Object>>();
    String[] identity = query.split("\\|", 2);
    String address = identity.length > 1 ? identity[1] : "";
    String[] words = address.split("\\s+");
    String searchQuery =
        identity[0] + " " + String.join(" ", Arrays.copyOf(words, Math.min(2, words.length)));
    try {
      for (String word : List.of("청모", "청첩장모임", "청첩장 모임")) {
        var response =
            naver
                .get()
                .uri(
                    u ->
                        u.path("/v1/search/blog.json")
                            .queryParam("query", searchQuery + " " + word)
                            .queryParam("display", 5)
                            .queryParam("sort", "sim")
                            .build())
                .header("X-Naver-Client-Id", naverId)
                .header("X-Naver-Client-Secret", naverSecret)
                .retrieve()
                .body(Map.class);
        for (var item : (List<Map<String, Object>>) response.getOrDefault("items", List.of())) {
          String title = plain(item.get("title")),
              description = plain(item.get("description")),
              link = text(item.get("link"));
          String content = (title + description).replaceAll("\\s+", "");
          if (!content.contains("청모") && !content.contains("청첩장모임")) continue;
          if (!content.contains(query.split("\\|", 2)[0].replaceAll("\\s+", ""))) continue;
          if (!link.startsWith("https://") && !link.startsWith("http://")) continue;
          links.putIfAbsent(
              link,
              Map.of(
                  "title",
                  title,
                  "description",
                  description,
                  "url",
                  link,
                  "date",
                  text(item.get("postdate"))));
        }
      }
      save(
          "REVIEW",
          query,
          type,
          Map.of("status", "ready", "blogs", links.values().stream().limit(3).toList()),
          Duration.ofHours(24));
    } catch (RuntimeException e) {
      PlaceDtos.Cached old = cached("REVIEW", query, type);
      save(
          "REVIEW",
          query,
          type,
          Map.of(
              "status",
              "failed",
              "blogs",
              old == null ? List.of() : old.value().getOrDefault("blogs", List.of())),
          Duration.ofHours(1));
    }
  }

  @Scheduled(fixedDelay = 3600000, initialDelay = 60000)
  public void refreshActive() {
    repository.deleteUnusedCache();
    if (!naverConfigured()) return;
    var rows = repository.findExpiredActiveCache();
    for (var row : rows) {
      String kind = text(row.get("kind")),
          q = text(row.get("query_text")),
          type = text(row.get("place_type"));
      queue(
          key(kind, q, type),
          () -> {
            if (kind.equals("SEARCH")) loadSearch(q, type);
            else loadReviews(q, type);
          });
    }
  }

  @PreDestroy
  public void close() {
    workers.shutdownNow();
  }
}
