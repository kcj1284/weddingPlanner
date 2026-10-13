# 청모 — 좋은 소식은, 좋은 사람들과 함께

한국의 청첩장 모임을 위한 모바일 웹 서비스입니다. 호스트가 모임을 만들고, 게스트는 개인별 6자리 초대코드 또는 전용 링크로 가입 없이 참여해 일정과 장소에 투표합니다.

## 기술 구성

Java 21 / Spring Boot 4.1.1 / JavaScript / CSS / PostgreSQL 17 / Spring Security / JDBC / Flyway를 사용합니다. 2026-10-12부터 로컬과 운영 DB 기준을 PostgreSQL로 전환했습니다. Oracle/H2의 과거 DDL은 `docs/legacy/`에 보관합니다.

## 로컬 실행

Windows에서는 Java 21, Maven 3.6.3 이상과 PowerShell 7을 준비하세요. `.tools`의 JDK/Maven이 있으면 자동으로 사용합니다. PostgreSQL은 공식 EDB 바이너리를 프로젝트 안에 준비하여 관리자 권한 없이 실행할 수 있습니다.

```powershell
./scripts/setup-postgres.ps1
./scripts/start-postgres.ps1
./scripts/test.ps1
./scripts/run.ps1
```

[http://localhost:8080/](http://localhost:8080/)에서 접속합니다. 앱과 로컬 PostgreSQL은 기본적으로 `127.0.0.1`에서만 접속할 수 있습니다. 데이터는 `data/postgresql`에 유지됩니다. 앱 DB와 테스트 DB를 분리하고 임의 비밀번호를 생성합니다. 현재 사용자 전용 자격 정보 파일은 `data/local-postgres.json`이며 Git에 포함하지 않습니다.

첫 실행의 다운로드와 DB 초기화, 기존 포트 사용, 외부 DB 설정, 중지·재시작은 [PostgreSQL 안내](docs/postgresql.md)를 참고하세요.

## 사용 흐름

1. 호스트 가입·로그인 → 모임 생성 → 날짜·시간과 장소 후보 추가.
2. 초대하기 → 초대 대상 이름 → 사람마다 6자리 코드·전용 링크 발급·복사.
3. 게스트는 초대받은 모임 메뉴에서 코드 확인 → 이름만 입력 → 참여·복수 투표. 전용 링크도 사용할 수 있습니다.
4. 동일 코드·링크로 다시 접속하면 기존 이름과 투표를 유지합니다. 코드는 대문자 영문·숫자를 모두 포함하고 I/O/0/1을 제외합니다. 만료는 발급 후 30일, 취소는 즉시 권한·투표 제거입니다.
5. 호스트가 장소 검색 또는 직접 추가 → 업체 예약 문의 → 확인 상태와 메모 기록.
6. 최종 날짜·장소 확정 → 투표 마감. 호스트가 재개하면 기존 표를 유지합니다.

로컬 프로필에서는 예시 계정과 모임을 준비합니다. 두 역할을 동시에 보려면 서로 다른 브라우저나 시크릿 창을 사용하세요.

| 계정 | 비밀번호 | 역할 |
|---|---|---|
| demo_host | cheongmo-demo | 호스트 체험 |
| demo_guest | cheongmo-demo | 이전 가입 계정의 게스트 체험 |

가입 없는 게스트 흐름은 호스트가 발급한 개인별 코드로 확인할 수 있습니다. 예시 장소는 실제 업체가 아닙니다. 체험 계정은 local 프로필에서만 생성합니다.

## 외부 연동

- 카카오·구글·네이버 호스트 OAuth 로그인: [소셜 로그인 설정](docs/social-login.md). 키가 없는 제공자는 비활성으로 표시합니다.
- 식당·카페 검색: 서버의 `KAKAO_REST_API_KEY` 환경 변수를 설정합니다. 키가 없으면 지도 확인과 직접 후보 추가를 안내합니다.
- 예약 상태는 호스트가 실제 업체에 확인한 기록입니다. 실시간 예약 재고·예약 대행·결제를 제공하지 않습니다.

## PostgreSQL 배포

이름 규칙은 [Naming conventions](docs/naming-conventions.md), 직접 DB를 조회하는 방법은 [DB 접속 안내](docs/database-access.md)를 참고하세요.

`Dockerfile`과 `postgres` 프로필을 준비했습니다. Neon·Supabase 등 관리형 PostgreSQL과 앱 서버를 분리하여 배포할 수 있습니다. 제공자의 PostgreSQL 버전과 접속 방식은 실제 콘솔에서 확인하세요.

```text
SPRING_PROFILES_ACTIVE=postgres
DATABASE_URL=jdbc:postgresql://<호스트>:5432/<DB명>
DATABASE_USERNAME=<앱 사용자>
DATABASE_PASSWORD=<비밀 환경 변수>
SERVER_ADDRESS=0.0.0.0
PORT=8080
```

운영 DB 접속은 TLS 인증서·호스트명 검증을 기본 사용하며 앱은 HTTPS 뒤에 배치합니다. 환경 변수에만 키와 비밀번호를 저장합니다. 연결·Flyway·이관·복구 방법은 [PostgreSQL 안내](docs/postgresql.md), 예상 비용과 무료 플랜 제한은 [비용 검토](docs/database-cost-review.md)를 참고하세요.

## 검증과 제한

2026-10-12 실제 PostgreSQL 17.11에서 테스트 24개 통과. 권한·CSRF·가입 중복·투표 교체·동시 확정·게스트 세션·철회·초대코드 충돌 재발급·NULL/빈 문자열 후보 중복·Flyway 적용·요청 제한을 검사했습니다.

기존 H2 데이터 8개 테이블 36행을 이관하고 전체 내용 비교가 일치함을 확인했습니다. H2 원본과 백업을 유지합니다. 브라우저에서 이관한 계정·모임, 새 모임·후보·6자리 코드 발급, 무가입 참여·투표 저장·재접속 유지도 확인했습니다.

실제 관리형 PostgreSQL 접속·클라우드 배포, Docker 이미지 빌드, 실제 소셜 인증 및 Kakao 검색 성공은 환경과 키가 없어 미검증입니다. 출시 전 계정 복구·데이터 삭제/보존 정책·운영 모니터링·백업 복원·사용성 평가를 보완해야 합니다.

## 개발 기록

- [개발 헌장 v1.0.0](.specify/memory/constitution.md)
- [활성 명세·계획·작업 목록](specs/002-cheongmo-meetup/)
- [개발 내용과 검증 결과](docs/development-summary.md)
- [사용자 요청 프롬프트 기록](docs/conversation-prompts.md)
- [디자인 점검과 개선 화면](docs/design-audit.md)
- [PostgreSQL 실행·이관·배포](docs/postgresql.md)

`specs/001-wedding-planner/`에는 최초 웨딩 플래너 명세를 보관합니다.

## 모바일 청첩장

호스트는 메뉴의 **모바일 청첩장**에서 디자인을 선택하고 사진·예식 정보를 입력해 청첩장을 저장·발행할 수 있습니다. 발행된 링크는 가입 없이 열람하며 청모 개인 초대코드와는 별개입니다. [사용 및 배포 안내](docs/mobile-invitation.md)를 참고하세요.
- [기능별 MVC 구조와 개발 기준](docs/architecture.md)
- [구조 리팩터링 검증](docs/structure-refactor-20261013.md)
- [Render Free + Neon Free 배포 진행](docs/render-neon-deployment.md)
