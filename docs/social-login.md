# 호스트 소셜 로그인 / 게스트 전용 링크

호스트는 기존 아이디 가입 또는 카카오·구글·네이버 OAuth 가입·로그인을 사용한다. 소셜 계정은 제공자와 사용자 고유 ID 조합으로 식별하며 이메일로 자동 병합하지 않는다. 게스트는 가입·비밀번호 없이 개인별 링크에서 이름을 입력한다. 같은 링크를 다시 열면 기존 참여자와 투표를 유지한다.

## 로컬 소셜 연동 설정

각 개발자 콘솔에서 웹 앱을 만들고 아래 콜백을 등록한다. 실제 서비스에서는 HTTPS 도메인으로 교체한다.

| 제공자 | 환경 변수 | 로컬 콜백 |
|---|---|---|
| 카카오 | `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET` | `http://localhost:8080/login/oauth2/code/kakao` |
| 구글 | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | `http://localhost:8080/login/oauth2/code/google` |
| 네이버 | `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` | `http://localhost:8080/login/oauth2/code/naver` |

카카오 CLIENT_ID는 REST API 키이며 카카오 로그인 및 Client Secret을 활성화하고 닉네임 동의 항목을 설정한다. 구글은 웹 애플리케이션 OAuth 클라이언트와 동의 화면을 설정한다. 네이버는 이름/닉네임 프로필 권한을 콘솔에서 설정한다. 인증 목적에는 고유 ID를 사용하며 이름 미제공 시 '호스트'로 표시한다. 위 환경 변수를 서버 실행 전에 설정하고 재시작한다. 둘 중 하나라도 없으면 해당 버튼은 비활성 상태다. 키를 저장소나 프런트 코드에 넣지 않는다.

사용자가 소셜 로그인을 완료하면 호스트 계정이 자동 생성된다. 인가 코드·state 검증과 세션 인증은 Spring Security OAuth2 Client가 수행한다. 접근 토큰은 서버 세션에서만 관리한다. 로컬 환경에는 실제 앱 키가 없어 제공자와의 로그인 왕복은 검증하지 않았다.

## 게스트 사용

호스트 화면의 **초대하기 → 초대 대상 이름 → 초대코드·링크 만들기 → 코드 복사 또는 링크 복사**에서 사람마다 링크를 발급한다. 원문은 발급 직후에만 표시하고 DB에는 SHA-256 해시만 저장한다. 링크는 대문자 영문·숫자를 섞은 6자리 난수이며 30일 동안 신규 접속이 가능하다. 한 모임의 활성 링크와 참여자는 각각 최대 100개/명이다. 링크는 해당 사람의 권한이므로 다른 사람에게 전달하면 같은 게스트로 접근할 수 있다.

초대 목록의 **초대 취소**를 확인하면 링크를 폐기하고 참여 권한과 기존 투표를 삭제한다. 기존 로그인 세션에서도 해당 모임 접근이 즉시 거부된다. 만료는 새 접속을 막으며 이미 발급된 세션은 세션 만료 또는 초대 취소까지 유지된다. 기존 공유 초대 코드 API는 이전 가입 사용자의 호환성을 위해 유지하지만 새 초대 화면에서는 개인별 코드와 링크를 함께 발급한다.

기존 Oracle 설치에는 `src/main/resources/schema-auth.sql`을 한 번 적용한다. 신규 Oracle 설치는 전체 `schema.sql`을 사용한다. 로컬 H2는 재시작 시 테이블이 자동 추가된다.

공식 문서: [Spring Security OAuth2 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/advanced.html), [카카오 로그인](https://developers.kakao.com/docs/en/kakaologin/rest-api), [네이버 로그인 API](https://developers.naver.com/docs/login/api/api.md), [구글 OAuth2 웹 서버](https://developers.google.com/identity/protocols/oauth2/web-server).

게스트는 **초대받은 모임 → 초대코드 입력 → 최초 참여 시 이름 입력**으로 가입 없이 참여한다. 새 코드는 6자리 대문자 영문·숫자를 모두 포함하며 I/O/0/1은 제외한다. 중복 코드는 DB UNIQUE 제약과 재발급으로 방지한다. 대소문자·공백을 정규화하고 이전 20자리 코드 및 43자리 링크도 허용한다. 입력 검증 및 게스트 세션 생성 요청은 서버 인스턴스별 클라이언트 IP당 1분 30회로 제한한다. 다중 서버 운영 시 공통 저장소의 요청 제한으로 전환해야 한다.
