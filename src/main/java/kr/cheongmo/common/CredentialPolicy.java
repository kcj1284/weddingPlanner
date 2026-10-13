package kr.cheongmo.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class CredentialPolicy {
  public static void username(String value) {
    if (value == null || !value.matches("[a-z0-9_-]{5,20}"))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "아이디는 영문 소문자·숫자·밑줄(_)·하이픈(-)으로 5~20자 입력해 주세요.");
  }

  public static void password(String value) {
    int length = value == null ? 0 : value.codePointCount(0, value.length());
    if (length < 8 || length > 64)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호는 8~64자로 입력해 주세요.");
  }
}
