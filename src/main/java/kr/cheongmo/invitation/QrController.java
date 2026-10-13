package kr.cheongmo.invitation;

import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class QrController {
  public static void checkUrl(String value) {
    try {
      URI url = URI.create(value);
      if (value.length() > 500
          || !Set.of("http", "https").contains(url.getScheme())
          || url.getHost() == null
          || url.getUserInfo() != null) throw new IllegalArgumentException();
    } catch (Exception e) {
      throw InvitationService.error(
          HttpStatus.BAD_REQUEST, "QR 연결 주소는 올바른 http 또는 https URL이어야 합니다.");
    }
  }

  public static BitMatrix matrix(String url) throws WriterException {
    return new QRCodeWriter()
        .encode(
            url,
            BarcodeFormat.QR_CODE,
            0,
            0,
            Map.of(
                EncodeHintType.MARGIN,
                0,
                EncodeHintType.ERROR_CORRECTION,
                ErrorCorrectionLevel.H,
                EncodeHintType.CHARACTER_SET,
                "UTF-8"));
  }

  public static BufferedImage render(String url, String style, String frame) throws Exception {
    checkUrl(url);
    if (!Set.of("SQUARE", "ROUNDED", "DOTS").contains(style)
        || !Set.of("SQUARE", "ROUNDED", "CIRCLE").contains(frame))
      throw new IllegalArgumentException();
    BitMatrix matrix = matrix(url);
    int n = matrix.getWidth(),
        scale = 8,
        padding = frame.equals("CIRCLE") ? n / 3 + 6 : 5,
        total = (n + padding * 2) * scale;
    BufferedImage image = new BufferedImage(total, total, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setColor(Color.WHITE);
      switch (frame) {
        case "CIRCLE" -> g.fillOval(0, 0, total, total);
        case "ROUNDED" -> g.fillRoundRect(0, 0, total, total, 40, 40);
        default -> g.fillRect(0, 0, total, total);
      }
      g.setColor(Color.BLACK);
      for (int y = 0; y < n; y++)
        for (int x = 0; x < n; x++)
          if (matrix.get(x, y)) {
            int px = (x + padding) * scale, py = (y + padding) * scale;
            boolean finder = (x < 9 && y < 9) || (x >= n - 9 && y < 9) || (x < 9 && y >= n - 9);
            if (finder || style.equals("SQUARE")) g.fillRect(px, py, scale, scale);
            else if (style.equals("DOTS")) g.fillOval(px, py, scale, scale);
            else g.fillRoundRect(px, py, scale, scale, 3, 3);
          }
    } finally {
      g.dispose();
    }
    return image;
  }

  @GetMapping(value = "/api/invitation-qr", produces = MediaType.IMAGE_PNG_VALUE)
  public ResponseEntity<byte[]> image(
      @RequestParam String url,
      @RequestParam(defaultValue = "SQUARE") String style,
      @RequestParam(defaultValue = "SQUARE") String frame)
      throws Exception {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(render(url, style, frame), "png", bytes);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(bytes.toByteArray());
  }
}
