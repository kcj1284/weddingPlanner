package kr.cheongmo.guest;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import kr.cheongmo.meeting.MeetingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class InviteCodeCipher {
  private final byte[] key;

  public InviteCodeCipher(@Value("${cheongmo.invite-code-key:}") String encoded) {
    key = encoded.isBlank() ? null : Base64.getDecoder().decode(encoded);
    if (key != null && key.length != 32)
      throw new IllegalArgumentException(
          "INVITE_CODE_ENCRYPTION_KEY must contain a base64 encoded 32-byte key");
  }

  public String encrypt(String code, String id) {
    return transform(code, id, true);
  }

  public String decrypt(String code, String id) {
    return transform(code, id, false);
  }

  private String transform(String value, String id, boolean encrypt) {
    if (key == null)
      throw MeetingService.error(HttpStatus.SERVICE_UNAVAILABLE, "초대코드 암호화 키 설정이 필요합니다.");
    try {
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      byte[] nonce = new byte[12], payload;
      if (encrypt) {
        new SecureRandom().nextBytes(nonce);
        payload = value.getBytes(StandardCharsets.UTF_8);
      } else {
        byte[] packed = Base64.getDecoder().decode(value);
        nonce = Arrays.copyOfRange(packed, 0, 12);
        payload = Arrays.copyOfRange(packed, 12, packed.length);
      }
      cipher.init(
          encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
          new SecretKeySpec(key, "AES"),
          new GCMParameterSpec(128, nonce));
      cipher.updateAAD(id.getBytes(StandardCharsets.UTF_8));
      byte[] output = cipher.doFinal(payload);
      if (!encrypt) return new String(output, StandardCharsets.UTF_8);
      byte[] packed = Arrays.copyOf(nonce, nonce.length + output.length);
      System.arraycopy(output, 0, packed, nonce.length, output.length);
      return Base64.getEncoder().encodeToString(packed);
    } catch (Exception e) {
      throw MeetingService.error(
          HttpStatus.SERVICE_UNAVAILABLE, "초대코드를 확인하지 못했습니다. 암호화 키를 확인해 주세요.");
    }
  }
}
