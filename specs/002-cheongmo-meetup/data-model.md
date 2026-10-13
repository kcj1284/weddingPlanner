# Data Model

- app_user: UUID id, username unique(소문자 영문·숫자·밑줄 3–30자), display_name(1–30자), password_hash. password 8–72자 및 UTF-8 최대 72바이트.
- meeting: UUID id, host_id FK, title(1–80자), description(최대 500자), area(1–80자), expected_count(2–100), invite_token unique, status OPEN/CONFIRMED, final_time_id, final_place_id, created_at.
- member: meeting_id + user_id 복합 PK, 두 FK. 호스트도 구성원이다.
- poll_option: UUID id, meeting_id FK, kind TIME/PLACE, title(1–120자), detail(최대 300자), map_url(최대 500자, http/https), phone(최대 40자), reservation UNKNOWN/AVAILABLE/BOOKED, reservation_note(최대 300자). TIME title은 ISO local datetime. 종류별 최대 30개.
- vote: meeting_id + user_id + option_id 복합 PK. member와 poll_option FK. 서버는 option 모임 소속을 검사한다.

OPEN → CONFIRMED: 호스트가 해당 모임 TIME/PLACE 각각 하나를 지정한다.
CONFIRMED → OPEN: 호스트 재개, 확정 선택 해제, 표와 후보 유지.
투표는 검증 후 전체 교체하고 모임 잠금으로 확정과 직렬화한다.
PostgreSQL VARCHAR/SMALLINT와 FK·유일 제약을 사용한다. created_at/expires_at은 TIMESTAMPTZ이며 만료는 절대 시각, 일정 후보 문자열은 한국 시간이다.

## 개인별 게스트 / 소셜 계정 추가

- account_kind(user_id PK/FK → app_user, kind HOST/GUEST): 무가입 내부 게스트 구분. 게스트는 비밀번호 로그인 및 모임 생성 금지.
- social_identity(provider, subject 복합 PK, user_id FK): 제공자 고유 ID 기반 호스트 매핑. 이메일 병합 없음.
- guest_link(id PK, meeting_id FK, label, token_hash UNIQUE, guest_user_id nullable FK, revoked, created_at, expires_at): 6자리 초대코드 SHA-256 해시, 30일 신규 접속 만료. 취소는 멤버십과 투표 제거. 동일 링크 첫 참여는 트랜잭션 행 잠금으로 한 게스트만 생성.

Flyway V1__initial_schema.sql로 전체 스키마를 적용한다. 과거 Oracle/H2 DDL은 docs/legacy에 보관하고 기존 데이터는 검증된 별도 이관 절차를 따른다.
