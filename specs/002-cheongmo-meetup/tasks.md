# Tasks: 청모 모바일 모임 조율

**Input**: specs/002-cheongmo-meetup 설계 문서
**Prerequisites**: spec.md, plan.md, research.md, data-model.md, contracts/http.md
**Tests**: 명세에 요구한 권한·투표·확정·검색 실패 검증 포함.

## Phase 1: Setup

- [X] T001 Java/Maven 구성과 실행 스크립트 작성: pom.xml, scripts/run.ps1, scripts/test.ps1
- [X] T002 개발 산출물·비밀 제외 설정: .gitignore

## Phase 2: Foundational

- [X] T003 공통 Oracle/H2 스키마 및 프로필: src/main/resources/schema.sql, application*.properties
- [X] T004 계정·세션·CSRF·오류 처리: src/main/java/kr/cheongmo/SecurityConfig.java, Accounts.java, ApiErrors.java

## Phase 3: User Story 1 — 모임 생성·참여 (P1)

**Independent Test**: 호스트 생성, 게스트 가입·참여, 비구성원 조회 거부.

- [X] T005 [US1] 먼저 권한·초대 인수 테스트 작성: src/test/java/kr/cheongmo/MeetingIntegrationTest.java
- [X] T006 [US1] 모임 이름 1–80자, 지역 1–80자, 인원 2–100 검증과 생성·참여 구현: src/main/java/kr/cheongmo/Meetings.java, MeetingController.java
- [X] T007 [US1] 가입·로그인·목록·생성·초대 화면: src/main/resources/static/index.html, app.js

## Phase 4: User Story 2 — 투표·확정 (P1)

**Independent Test**: 두 계정 표 수정·철회와 확정 이후 투표 거부.

- [X] T008 [US2] 먼저 투표 교체·외부 후보·확정·재개 테스트 작성: src/test/java/kr/cheongmo/PollIntegrationTest.java
- [X] T009 [US2] TIME/PLACE 각각 최대 30, 표 유일성, 잠금·원자 교체·확정 구현: src/main/java/kr/cheongmo/Meetings.java, MeetingController.java
- [X] T010 [US2] 일정·장소 복수 투표·집계·확정·재개 화면: src/main/resources/static/app.js

## Phase 5: User Story 3 — 장소 탐색 (P2)

**Independent Test**: 검색 미설정 안내, 수동 후보, 호스트 예약 상태 기록.

- [X] T011 [US3] 먼저 미설정·게스트 검색 차단 테스트 작성: src/test/java/kr/cheongmo/PlacesIntegrationTest.java
- [X] T012 [US3] Kakao key 서버 전용 검색·실패 처리·예약 UNKNOWN/AVAILABLE/BOOKED와 메모 최대 300자: src/main/java/kr/cheongmo/Places.java, Meetings.java
- [X] T013 [US3] 검색·지도·전화·후보 추가·예약 상태 화면: src/main/resources/static/app.js

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T014 모바일 360px부터 반응형·키보드·빈 화면·로딩·오류 스타일: src/main/resources/static/styles.css
- [X] T015 빌드·통합 테스트·브라우저 여정 검증 기록: README.md, scripts/browser-check.cjs
- [X] T016 Oracle 설정·검색 key·실행·출시 전 제한 기록: README.md

## Dependencies & Execution Order

T001–T004 → US1 → US2 → US3 → T014–T016. 테스트는 해당 구현보다 먼저 작성한다.
US2·US3의 인수 검증은 모임 fixture로 독립 실행할 수 있다.

## Parallel Examples

US1 서버 검증과 화면 레이아웃은 파일이 다르다. US2 폼과 상태 인수 자료는 독립 작성 가능하다.
US3 장소 제공자 검증과 검색 결과 CSS는 독립 작성 가능하다. 현재 구현은 순차 진행한다.

## Implementation Strategy

모임 생성·참여부터 검증한 뒤 투표, 검색, 모바일 완성 순으로 진행한다.
실제 Oracle와 실제 Kakao 검색 성공은 자격 정보가 없으므로 별도 검증 제한으로 기록한다.


## 검증 결과 (2026-10-08)

빌드 성공. 서버 통합 테스트 10개 통과, 실패 0개. 실제 브라우저에서 호스트/게스트, 생성·초대·투표·확정·재개·철회, 예약 기록, CSRF/권한 거부, 가입·로그아웃, 한국 시간 표시, 화면 폭별 넘침을 검증했다. 재시작 후 기존 모임 유지 확인. Oracle 실제 접속, 실제 Kakao 검색, 실시간 예약 재고, 사용자 5명 사용성 평가는 미검증이며 README에 제한을 기록했다.

## 개인별 게스트 초대와 호스트 소셜 로그인 (2026-10-08)

- [X] T017 게스트 링크 생성·해시 저장·30일 만료·참여자 재접속·철회 구현
- [X] T018 카카오·구글·네이버 호스트 OAuth 프로필 매핑 및 설정별 활성화
- [X] T019 가입 없는 게스트 진입 화면 및 호스트 초대 관리 화면 구현
- [X] T020 HTTP 세션·CSRF·게스트 권한·철회와 소셜 계정 분리 통합 검증
- [X] T021 최신 로컬 빌드·브라우저 여정·설정 문서 검증


추가 범위 검증: 최종 package 성공, 통합 테스트 18개 통과. 실제 브라우저에서 개인별 링크 발급 → 로그아웃 → 이름만 입력 → 일정·장소 투표 저장 → 재접속 시 이름·투표 유지 확인. 390px 게스트 화면 가로 넘침 없음. 소셜 키 미설정 시 세 버튼 비활성 표시 및 테스트 키 설정 시 세 인증 요청 주소/state 생성 검증. 실제 제공자 인증 완료 및 Oracle 접속은 미검증.

## 초대코드 입력 / 6자리 코드

- [X] T022 비로그인·게스트의 초대받은 모임 메뉴에서 코드 확인 후 이름으로 참여
- [X] T023 6자리 대문자 영문·숫자 혼합 코드 발급, I/O/0/1 제외, 전역 중복 방지
- [X] T024 기존 긴 코드·링크 호환, 재접속 유지, IP별 코드 검증 요청 제한
- [X] T025 테스트 20개·최신 로컬 빌드 검증 및 개발/사용자 프롬프트 문서 정리

브라우저에서 코드 입력 창, 잘못된 코드 오류, 코드 기반 무가입 참여와 재접속 투표 유지, 신규 6자리 코드 발급 화면을 확인했다. 신규 6자리 코드의 게스트 세션은 서버 통합 테스트에서 검증했다.

## PostgreSQL 전환 (2026-10-12)

- [X] T026 PostgreSQL 드라이버·스키마·SQL·코드 충돌 재시도·TIMESTAMPTZ 적용
- [X] T027 Flyway, 실제 PostgreSQL 테스트, 앱/테스트 역할·DB 분리
- [X] T028 Windows 사용자 프로세스 DB 실행과 원본 H2 읽기 전용 이관·내용 비교·백업
- [X] T029 테스트 24개·브라우저 기능·로컬 빌드 검증
- [X] T030 관리형 PostgreSQL TLS 설정·앱 Dockerfile·실행·비용·복구 문서 정리

실제 PostgreSQL 17.11 테스트 24개 통과. H2 36행 8테이블 내용 비교 PASS, 원본·백업 보존. 실제 관리형 PostgreSQL 접속, Docker 이미지 빌드 및 서비스 배포는 미검증.
