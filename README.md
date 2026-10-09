# 청모 — 좋은 소식은, 좋은 사람들과 함께

한국의 청첩장 모임을 위한 모바일 웹 MVP입니다. 호스트가 가입 후 모임을 열고 게스트는 개인별 초대코드 또는 링크로 가입 없이 참여해 일정과 장소에 복수 투표합니다. 호스트가 최종 약속을 확정하거나 투표를 다시 열 수 있습니다.

## 기술 선택

사용자의 익숙한 환경에 맞춰 **Java 21 / Spring Boot 4.1.1 / JavaScript / CSS / Oracle JDBC**를 선택했습니다. 현재 규모의 화면은 별도 프런트엔드 프레임워크 없이 구성했고, 모임별 권한·투표 트랜잭션은 서버에서 처리합니다. Spring Security 세션, BCrypt, CSRF를 사용합니다.

로컬 체험은 파일 기반 **H2의 Oracle 모드**를 사용합니다. Oracle 운영 프로필과 DDL은 준비됐지만 실제 Oracle 접속 검증은 하지 않았습니다.

## 실행

이 작업 공간에는 `.tools` 아래 Java 21과 Maven 3.9.9가 설치돼 있습니다. 다른 환경에서는 Java 21, Maven 3.6.3 이상을 설치하세요.

```powershell
./scripts/run.ps1
```

[http://localhost:8080](http://localhost:8080)에서 접속합니다. 기본 바인딩은 이 컴퓨터의 `127.0.0.1`입니다. 다른 기기에서 로컬 개발 서버를 접속하려면 `SERVER_ADDRESS`와 방화벽 설정을 별도 구성해야 합니다.

로컬 프로필에서는 첫 실행 때 예시 모임과 체험 계정을 생성합니다. 첫 화면의 **호스트로 체험 / 게스트로 체험**으로 각각 볼 수 있습니다. 두 역할을 동시에 확인하려면 서로 다른 브라우저 또는 시크릿 창을 사용하세요.

| 계정 | 비밀번호 | 예시 모임 역할 |
|---|---|---|
| demo_host | cheongmo-demo | 호스트 |
| demo_guest | cheongmo-demo | 게스트 |

예시 장소는 실제 업소가 아닙니다. 체험 계정은 `local` 프로필 전용입니다. 데이터는 `data/cheongmo.mv.db`에 남으며 서버 재시작 후에도 유지됩니다.

## 사용 흐름

1. 가입·로그인 → 모임 이름, 지역, 예상 인원 입력 → 모임 생성.
2. 호스트가 날짜·시간과 장소 후보 추가 → 초대 대상별 6자리 대문자 영문·숫자 코드와 전용 링크 발급·복사.
3. 게스트가 ‘초대받은 모임’에서 코드 입력 또는 전용 링크 접속 → 이름만 입력해 무가입 참여 → 가능한 후보를 모두 선택 → 투표 저장. 모두 해제해 저장하면 철회됩니다.
4. 호스트가 장소를 검색하거나 직접 입력 → 전화·지도에서 예약 문의 → 확인 상태와 메모 기록.
5. 모임 관리에서 날짜 하나와 장소 하나를 확정. 이후 투표는 잠기며, 호스트가 재개하면 기존 표를 유지한 채 다시 변경할 수 있습니다.

역할은 모임별로 정해집니다. 한 사람이 다른 모임에서는 게스트가 될 수 있습니다. 게스트·비구성원의 관리 요청은 서버에서도 거부합니다.

## 식당·카페 검색

```powershell
$env:KAKAO_REST_API_KEY = '<서버용 REST API 키>'
./scripts/run.ps1
```

키는 서버에만 보관합니다. Kakao Local의 식당 `FD6`, 카페 `CE7` 검색을 사용합니다. 미설정·연결 실패 때 지도 링크와 직접 후보 추가를 제공합니다.

**장소 검색은 실시간 예약 가능 좌석 조회가 아닙니다.** 예약 가능 여부는 호스트가 업소에 문의한 뒤 `미확인 / 예약 가능 확인 / 예약 완료`로 기록합니다. 자동 예약·결제는 구현 범위 밖이며, 실시간 예약 재고 연동에는 제공자 계약과 API가 필요합니다. 현재 실제 Kakao 검색 성공은 키가 없어 검증하지 않았습니다.

## Oracle 설정

DB 관리자가 `src/main/resources/schema.sql`을 새 스키마에 최초 적용하세요. 테이블 생성 권한이 필요합니다. 운영 프로필에서는 자동 DDL을 실행하지 않습니다.

```powershell
$env:ORACLE_URL = 'jdbc:oracle:thin:@//호스트:1521/서비스명'
$env:ORACLE_USERNAME = '<사용자>'
$env:ORACLE_PASSWORD = '<비밀번호>'
./scripts/run.ps1 -Profile oracle
```

운영 프로필의 세션 쿠키는 Secure이므로 HTTPS 환경이 필요합니다. 자격 정보는 저장소나 프런트엔드에 기록하지 마세요.

## 검증

```powershell
./scripts/test.ps1
# 패키징
. ./scripts/env.ps1
& $mavenCommand -DskipTests package
```

브라우저 검증은 실행 중인 local 서버와 Node.js, Playwright가 필요합니다.

```powershell
npm install --no-save playwright
npx playwright install chromium
node scripts/browser-check.cjs
# 이미 설치된 Chrome을 사용하려면:
$env:CHEONGMO_BROWSER = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
node scripts/browser-check.cjs
```

브라우저 검증은 `브라우저 검증 <시각>` 이름의 모임을 로컬 DB에 남깁니다. 스크린샷은 무시되는 `artifacts/`에 저장합니다.

2026-10-08 검증: 서버 통합 테스트 **10개 통과**(권한·CSRF·입력 검증·가입 중복·투표 교체·동시 확정 포함), 호스트·게스트 실제 브라우저 여정, 투표 저장·철회, 예약 기록, 확정·재개, 360/390/768/1280px 가로 넘침 없음, JavaScript 실행 오류 없음. 테스트 결과는 `target/surefire-reports`에서 확인합니다.

## 명세와 후속 범위

- `.specify/memory/constitution.md`: 개발 헌장 v1.0.0.
- `specs/002-cheongmo-meetup/`: 활성 명세·설계·작업 목록.
- `specs/001-wedding-planner/`: 최초 웨딩 플래너 명세 보관.

현재는 로컬 MVP입니다. 실제 Oracle·장소 제공자 검증, 사용자 5명 대상 사용성 평가, 계정 복구, 일반 가입 구성원 제거, 데이터 삭제·보존 정책, 운영 환경의 로그인 제한·모니터링은 출시 전에 추가 검증·구현이 필요합니다.

참고: [Spring Boot 시스템 요구사항](https://docs.spring.io/spring-boot/system-requirements.html), [Kakao Local API](https://developers.kakao.com/docs/ko/local/dev-guide), [Oracle JDBC FAQ](https://www.oracle.com/database/technologies/faq-jdbc.html).

개인별 게스트 링크 및 카카오·구글·네이버 호스트 로그인 설정은 [연동 안내](docs/social-login.md)를 참고하세요. 게스트 초대 철회는 구현되었으며 기존 투표와 참여 권한을 함께 제거합니다.


추가 인증 범위 최종 검증: 서버 통합 테스트 18개 통과, 개인별 무가입 게스트 진입·투표·재접속과 소셜 버튼 표시 확인. 실제 소셜 계정 인증 왕복은 앱 키 미설정으로 미검증.

## 개발 기록

- [개발 내용과 검증 결과](docs/development-summary.md)
- [사용자 요청 프롬프트 기록](docs/conversation-prompts.md)
- [디자인 점검과 개선 화면](docs/design-audit.md)

최종 코드 입력 기능 검증: 서버 테스트 20개 통과. 기존 긴 코드·링크 호환, 신규 6자리 대문자 영문·숫자 형식, 중복 방지, 입력 요청 제한을 반영했습니다.
