# 입력 중 아이디·비밀번호 규칙 안내

2026-10-13 Product Design 점검·개선. 회원가입과 새 비밀번호 재설정에 적용했다.

## 점검과 개선

1. 기존 가입 화면에 `Ab`, `123`을 입력해도 고정 규칙 설명만 보여 부족한 조건을 구분하기 어려웠다. [이전 화면](live-credential-feedback-20261013/live-validation-before.png).
2. 입력 중 미충족 조건만 빨간색으로 표시하고 부족한 글자 수를 안내한다. 대문자 아이디의 문자 규칙과 길이 오류를 함께 표시한다. [오류 상태](live-credential-feedback-20261013/live-validation-invalid.png).
3. 각 필드가 조건을 충족하면 초록색 테두리·완료 문구로 전환한다. 확인 입력 후 원래 비밀번호를 바꾸면 일치 여부를 다시 계산한다. [충족 상태](live-credential-feedback-20261013/live-validation-valid.png).
4. 재설정에도 같은 안내를 적용했다. 7자 오류와 8자 반복 문자 허용·확인 일치를 검증했다. [재설정 상태](live-credential-feedback-20261013/live-validation-reset.png).
5. 390×844 모바일에서도 줄바꿈과 세로 스크롤로 내용을 읽을 수 있고 페이지 가로 넘침이 없다. [모바일 오류](live-credential-feedback-20261013/live-validation-mobile-invalid.png), [모바일 충족](live-credential-feedback-20261013/live-validation-mobile.png).

빈 필드에는 사전 오류를 표시하지 않고 포커스 시 중립 안내를 제공한다. 입력 후 비우거나 필드를 떠나면 필요한 조건을 안내한다. 색상과 문구를 함께 제공하고 `aria-describedby`, `aria-invalid`, polite 상태 알림을 연결했다. 스크린리더 실기 검증까지 수행한 것은 아니다.

초록색 아이디는 **형식 충족**을 의미하며 중복 여부는 가입 요청 때 서버에서 검사한다. 비밀번호 최소 8자·최대 64자와 연속·반복 문자 허용 정책은 유지한다.

## 검증 범위

자동 테스트 58개 통과, JavaScript 구문 검사 및 로컬 패키지 빌드·재시작 완료. 브라우저 오류 로그 없음. 화면 검증은 임의 입력값을 사용했으며 가입을 제출하지 않았다. 재설정 화면은 무효한 테스트 토큰으로 입력 상태만 확인했고 실제 비밀번호를 변경하지 않았다.
