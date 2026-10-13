package kr.cheongmo.invitation;

import jakarta.validation.constraints.*;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.*;
import javax.imageio.ImageIO;
import kr.cheongmo.invitation.dto.InvitationDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class InvitationService {
  private final InvitationRepository repository;
  private final ObjectMapper objectMapper;

  public InvitationService(InvitationRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  public static ResponseStatusException error(HttpStatus status, String message) {
    return new ResponseStatusException(status, message);
  }

  public void host(String owner) {
    if (repository.countGuestAccount(owner) > 0)
      throw error(HttpStatus.FORBIDDEN, "청첩장은 호스트 계정으로 만들 수 있어요.");
  }

  public List<InvitationDtos.Summary> list(String owner) {
    host(owner);
    return repository.findInvitationSummaries(owner);
  }

  public InvitationDtos.Card own(String id, String owner) {
    host(owner);
    return repository.findOwnedInvitation(id, owner).stream()
        .findFirst()
        .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "내 청첩장을 찾을 수 없습니다."));
  }

  public InvitationDtos.Body published(String token) {
    InvitationDtos.Body body =
        repository.findPublishedBody(token).stream()
            .findFirst()
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "아직 발행되지 않았거나 공개가 종료된 청첩장입니다."));
    if (body.giftAccounts() != null && !body.giftAccounts().enabled())
      return new InvitationDtos.Body(
          body.template(),
          body.groom(),
          body.bride(),
          body.groomParents(),
          body.brideParents(),
          body.greeting(),
          body.dateTime(),
          body.venue(),
          body.address(),
          body.transport(),
          body.groomPhone(),
          body.bridePhone(),
          body.cover(),
          body.gallery(),
          body.design(),
          null,
          body.newspaper());
    return body;
  }

  public InvitationDtos.Card create(String owner, InvitationDtos.Body body) {
    host(owner);
    validate(body);
    if (repository.countOwnedInvitations(owner) >= 20)
      throw error(HttpStatus.CONFLICT, "청첩장은 계정당 최대 20개까지 만들 수 있어요.");
    String id = UUID.randomUUID().toString(), token = UUID.randomUUID().toString().replace("-", "");
    repository.insertInvitation(id, owner, token, objectMapper.writeValueAsString(body));
    return own(id, owner);
  }

  public InvitationDtos.Card save(String id, String owner, int version, InvitationDtos.Body body) {
    own(id, owner);
    validate(body);
    if (repository.updateInvitation(objectMapper.writeValueAsString(body), id, owner, version) != 1)
      throw error(HttpStatus.CONFLICT, "다른 화면에서 변경되었습니다. 새로고침 후 다시 편집해 주세요.");
    return own(id, owner);
  }

  public InvitationDtos.Card publish(String id, String owner, int version, boolean published) {
    own(id, owner);
    if (repository.updatePublication(published ? 1 : 0, id, owner, version) != 1)
      throw error(HttpStatus.CONFLICT, "다른 화면에서 변경되었습니다. 새로고침 후 다시 시도해 주세요.");
    return own(id, owner);
  }

  public void validate(InvitationDtos.Body body) {
    if (body.giftAccounts() != null) {
      var gift = body.giftAccounts();
      if (gift.enabled() && gift.accounts().isEmpty())
        throw error(HttpStatus.BAD_REQUEST, "공개할 축의금 계좌를 추가해 주세요.");
      for (String side : List.of("GROOM", "BRIDE"))
        if (gift.accounts().stream().filter(a -> a.side().equals(side)).count() > 4)
          throw error(HttpStatus.BAD_REQUEST, "축의금 계좌는 신랑측·신부측 각각 최대 4개까지 추가할 수 있어요.");
    }
    try {
      LocalDateTime date = LocalDateTime.parse(body.dateTime());
      if (date.getYear() < 2000 || date.getYear() > 2100) throw new IllegalArgumentException();
    } catch (RuntimeException e) {
      throw error(HttpStatus.BAD_REQUEST, "예식 일시를 확인해 주세요. 한국 시간 기준입니다.");
    }
    image(body.cover());
    for (String image : body.gallery()) image(image);
    if (body.design() != null) {
      InvitationDtos.Design design = body.design();
      Set<String> ids = new HashSet<>();
      for (InvitationDtos.TextLayer text : design.texts())
        if (!ids.add(text.id())
            || text.x() + text.width() > 360
            || text.y() + text.size() > design.height())
          throw error(HttpStatus.BAD_REQUEST, "텍스트가 편집 영역을 벗어나거나 중복되었습니다.");
      for (InvitationDtos.QrLayer qr : design.qrs()) {
        if (!ids.add(qr.id()) || qr.x() + qr.size() > 360 || qr.y() + qr.size() > design.height())
          throw error(HttpStatus.BAD_REQUEST, "QR 코드가 편집 영역을 벗어나거나 중복되었습니다.");
        if (qr.target().equals("URL")) QrController.checkUrl(qr.url());
      }
    }
  }

  public void image(String image) {
    if (image == null || image.isEmpty()) return;
    try {
      String prefix =
          image.startsWith("data:image/jpeg;base64,")
              ? "data:image/jpeg;base64,"
              : "data:image/png;base64,";
      if (!image.startsWith(prefix) || image.length() > 750000)
        throw new IllegalArgumentException();
      byte[] bytes = Base64.getDecoder().decode(image.substring(prefix.length()));
      if (bytes.length > 512000) throw new IllegalArgumentException();
      try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
        var readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) throw new IllegalArgumentException();
        var reader = readers.next();
        try {
          reader.setInput(input);
          String format = reader.getFormatName();
          if (!(prefix.contains("jpeg")
              ? format.equalsIgnoreCase("JPEG")
              : format.equalsIgnoreCase("PNG"))) throw new IllegalArgumentException();
          if ((long) reader.getWidth(0) * reader.getHeight(0) > 16000000
              || reader.getWidth(0) > 4096
              || reader.getHeight(0) > 4096) throw new IllegalArgumentException();
          if (reader.read(0) == null) throw new IllegalArgumentException();
        } finally {
          reader.dispose();
        }
      }
    } catch (Exception e) {
      throw error(HttpStatus.BAD_REQUEST, "사진은 500KB 이하의 올바른 JPEG 또는 PNG여야 합니다.");
    }
  }
}
