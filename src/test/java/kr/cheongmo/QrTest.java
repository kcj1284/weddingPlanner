package kr.cheongmo;

import static org.assertj.core.api.Assertions.*;

import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.awt.image.BufferedImage;
import kr.cheongmo.invitation.QrController;
import org.junit.jupiter.api.Test;

class QrTest {
  static class Pixels extends LuminanceSource {
    final byte[] pixels;

    Pixels(BufferedImage image) {
      super(image.getWidth(), image.getHeight());
      pixels = new byte[getWidth() * getHeight()];
      for (int y = 0; y < getHeight(); y++)
        for (int x = 0; x < getWidth(); x++) {
          int value = image.getRGB(x, y), alpha = value >>> 24;
          int gray =
              alpha < 128
                  ? 255
                  : (((value >> 16) & 255) * 306 + ((value >> 8) & 255) * 601 + (value & 255) * 117)
                      >> 10;
          pixels[y * getWidth() + x] = (byte) gray;
        }
    }

    public byte[] getRow(int y, byte[] row) {
      if (row == null || row.length < getWidth()) row = new byte[getWidth()];
      System.arraycopy(pixels, y * getWidth(), row, 0, getWidth());
      return row;
    }

    public byte[] getMatrix() {
      return pixels;
    }
  }

  @Test
  void allQrStylesAndFramesDecodeToOriginalUrl() throws Exception {
    for (String url :
        new String[] {
          "https://cheongmo.example/invitation.html?card=e6404a7eb15343b2b5ae071fe3b40a58",
          "https://map.example/?q=%EC%84%9C%EC%9A%B8"
        })
      for (String style : new String[] {"SQUARE", "ROUNDED", "DOTS"})
        for (String frame : new String[] {"SQUARE", "ROUNDED", "CIRCLE"}) {
          BufferedImage image = QrController.render(url, style, frame);
          var result =
              new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new Pixels(image))));
          assertThat(result.getText()).as(style + " " + frame).isEqualTo(url);
        }
  }

  @Test
  void unsafeQrTargetsAreRejected() {
    for (String url :
        new String[] {
          "javascript:alert(1)", "data:text/html,test", "https://user:password@example.com"
        })
      assertThatThrownBy(() -> QrController.checkUrl(url))
          .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }
}
