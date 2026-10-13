package kr.cheongmo.place;

import java.util.*;
import kr.cheongmo.meeting.MeetingService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

@Service
public class PlaceService {
  private final MeetingService meetingService;
  private final PlaceDiscoveryService placeDiscoveryService;

  public PlaceService(MeetingService meetingService, PlaceDiscoveryService placeDiscoveryService) {
    this.meetingService = meetingService;
    this.placeDiscoveryService = placeDiscoveryService;
  }

  public Map<String, Object> search(String id, String user, String q, String type) {
    meetingService.requireHost(id, user, false);
    if (q == null || q.isBlank() || q.length() > 100 || !List.of("FD6", "CE7").contains(type))
      throw MeetingService.error(HttpStatus.BAD_REQUEST, "검색어와 식당·카페 종류를 확인해 주세요.");
    return placeDiscoveryService.search(q.trim(), type);
  }

  public Map<String, Object> reviews(String id, String user, String q, String type) {
    meetingService.requireHost(id, user, false);
    if (q == null || q.isBlank() || q.length() > 100 || !List.of("FD6", "CE7").contains(type))
      throw MeetingService.error(HttpStatus.BAD_REQUEST, "검색어를 확인해 주세요.");
    return placeDiscoveryService.reviews(q.trim(), type);
  }
}
