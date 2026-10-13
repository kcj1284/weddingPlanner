# PostgreSQL 실행·이관·배포

2026-10-12부터 DB 기준은 PostgreSQL이다. 현재 로컬 검증 버전은 PostgreSQL 17.11이며 Java 21 / Spring Boot / JavaScript를 유지한다. PostgreSQL 17은 공식 지원 대상이고 개발·운영에서 같은 메이저 버전을 선택하는 것을 권장한다.

## Windows 로컬

Java 21, Maven 3.6.3 이상, PowerShell 7을 준비한다. 프로젝트 `.tools`에 준비된 JDK/Maven이 있으면 자동 사용한다.

```powershell
./scripts/setup-postgres.ps1
./scripts/start-postgres.ps1
./scripts/test.ps1
./scripts/run.ps1
```

최초 실행은 공식 EDB Windows PostgreSQL 17.11 바이너리를 `.tools/postgresql-17`에 준비하고 `data/postgresql` 클러스터를 초기화한다. 시스템 서비스 등록이나 관리자 권한 없이 현재 사용자 프로세스로 실행한다. DB는 `127.0.0.1:5432`에서만 수신한다. 앱 주소는 `http://localhost:8080/`이다.

앱 DB/역할은 `cheongmo`, 테스트 DB/역할은 `cheongmo_test`이며 각각 별도 비밀번호를 생성한다. DB 역할은 관리자 권한 없이 자기 DB를 소유한다. 초기화용 postgres 관리자 자격 정보와 앱·테스트 비밀번호는 현재 Windows 사용자만 읽을 수 있도록 ACL을 제한한 `data/local-postgres.json`에 보관한다. 비밀번호를 로그에 출력하지 않는다. 런타임·DB·자격 정보·로그는 Git 제외 대상이다.

이미 5432를 사용 중인 DB가 있으면 덮어쓰지 않는다. 첫 초기화 전 `./scripts/start-postgres.ps1 -Port 55432`로 다른 포트를 선택하거나 기존 PostgreSQL의 환경 변수를 설정한다. 초기화 후에는 저장된 포트 설정을 사용한다. `scripts/env.ps1`은 로컬 설정에서 환경 변수를 읽는다. 명시적으로 설정한 외부 DB URL은 로컬 설정으로 덮어쓰지 않는다.

실행 중인 로컬 PostgreSQL을 멈추려면 앱 서버를 종료하고 다음 명령을 사용한다. 데이터 폴더는 유지된다.

```powershell
./.tools/postgresql-17/bin/pg_ctl.exe stop -D ./data/postgresql -m fast -w
```

## 스키마와 테스트

스키마는 `src/main/resources/db/migration/V1__initial_schema.sql`부터 Flyway로 버전 관리한다. 적용된 파일을 수정하지 않고 V2 이후의 새 마이그레이션을 추가한다. 시작 시 마이그레이션 이력이 검증된다.

테스트는 H2 호환 모드가 아닌 실제 PostgreSQL에 실행한다. 직접 지정하는 경우:

```powershell
$env:TEST_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:5432/cheongmo_test'
$env:TEST_DATABASE_USERNAME = 'cheongmo_test'
# TEST_DATABASE_PASSWORD는 환경 변수에 설정한다.
./scripts/test.ps1
```

통합 테스트는 도메인 테이블의 데이터를 초기화한다. 테스트 전용 빈 DB를 사용하며 이름이 `_test`로 끝나지 않으면 테스트의 데이터 초기화를 거부한다. 운영 자격 정보와 테스트 자격 정보를 공유하지 않는다.

이번 전환에서는 Oracle NVL과 빈 문자열 처리를 정리하고, 초대코드 충돌은 PostgreSQL의 `ON CONFLICT (token_hash) DO NOTHING`으로 재시도한다. 만료·생성 시각은 TIMESTAMPTZ로 저장하고 만료 안내는 한국 시간으로 표시한다. 만료 생성은 UTC 기준이며 앱 서버의 시간대가 달라도 일관된다.

## 기존 H2 데이터 이관

기존 `data/cheongmo.mv.db`와 백업은 보존한다. Oracle/H2의 과거 DDL은 `docs/legacy/`에 보관하며 현재 런타임에서 사용하지 않는다. 기존 Oracle 운영 데이터가 있는 환경은 이 스크립트의 대상이 아니며 별도 이관 검증이 필요하다.

1. 기존 H2 앱 서버를 종료하고 PostgreSQL의 **빈 새 DB**를 준비한다.
2. 빌드 후 아래 명령으로 Flyway 스키마만 적용한다. 시작 로그에서 준비 완료를 확인하고 이 앱 프로세스를 종료한다.

```powershell
. ./scripts/env.ps1
& $mavenCommand -q -DskipTests package
java -jar target/cheongmo-0.1.0.jar --spring.profiles.active=local --cheongmo.seed-demo=false
```

3. 이관한다. `scripts/env.ps1`의 DATABASE 환경 변수가 대상 DB를 가리켜야 한다.

```powershell
./scripts/migrate-h2.ps1
```

이관은 H2 파일을 `data/backups`에 복사하고 원본을 읽기 전용으로 연다. PostgreSQL 도메인 테이블에 데이터가 있으면 작업을 거부한다. 사용자·모임·구성원·후보·투표·게스트·소셜 계정을 한 트랜잭션으로 복사하고 테이블별 전체 내용의 SHA-256 비교가 모두 일치할 때만 커밋한다. 날짜는 기존 한국 시간에서 절대 시각으로 변환한다. 실패하면 대상 변경을 롤백하고 원본·백업을 유지한다. 이미 이관한 DB에 다시 실행하지 않는다.

2026-10-12 실제 로컬 이관: 8개 테이블, 총 36행의 내용 비교 PASS. 원본과 날짜가 포함된 H2 백업 보존. 이관 후에도 기존 가입 계정과 개인별 초대코드·투표를 사용할 수 있다. 되돌릴 때는 보존된 H2 파일과 이전 Git 커밋의 애플리케이션을 함께 사용한다. 전환 후 새로 입력된 PostgreSQL 데이터는 과거 H2 파일에 포함되지 않으므로 운영 롤백에는 별도의 내보내기·동기화 계획이 필요하다.

## 관리형 PostgreSQL 배포

비용을 낮추는 초기 선택지는 Neon 등의 무료 관리형 PostgreSQL이다. Supabase 등 일반 PostgreSQL 서비스에도 같은 JDBC 구성을 적용할 수 있다. 무료 요금은 용량·컴퓨팅·복원·휴면 제한이 있으므로 실제 이용자를 받기 전에 필요한 백업 및 운영 예산을 정한다. [비용 검토](database-cost-review.md)를 참고한다.

운영 설정:

```text
SPRING_PROFILES_ACTIVE=postgres
DATABASE_URL=jdbc:postgresql://<제공자 호스트>:5432/<DB명>
DATABASE_USERNAME=<앱 DB 사용자>
DATABASE_PASSWORD=<환경 변수에서 제공>
SERVER_ADDRESS=0.0.0.0
PORT=8080
```

제공자의 `postgresql://user:password@host/...` URL을 그대로 넣지 않는다. JDBC URL, 사용자, 비밀번호를 분리한다. 마이그레이션에는 직접 접속 또는 세션 풀링 엔드포인트를 권장하며, 트랜잭션 풀링 엔드포인트를 사용하는 경우 Flyway와 JDBC 기능의 호환성을 별도 확인한다.

`postgres` 프로필은 DB TLS의 인증서·호스트명 검증(`verify-full`)을 사용하고 Java 신뢰 저장소를 활용한다. 제공자 CA가 Java에 신뢰되지 않은 경우 해당 CA를 신뢰 저장소에 추가한다. DB URL에 검증을 해제하는 SSL 옵션을 넣지 않는다. 운영 앱은 HTTPS 뒤에 배치하고 보안 쿠키를 사용한다. 프록시가 외부의 위조된 Forwarded 헤더를 제거하도록 설정한다. 소셜 로그인 콜백도 실제 HTTPS 도메인으로 등록한다.

Dockerfile은 앱 컨테이너 빌드를 제공하며 운영 DB와 앱 서버는 분리한다. Docker 이미지 자체는 아직 Docker가 설치되지 않은 로컬 환경에서 빌드 검증하지 않았다. 클라우드 자격 정보가 없어 실제 외부 관리형 DB 접속·서비스 배포는 미검증이다. 운영 역할과 스키마 변경 역할을 분리하는 환경에서는 Flyway URL/사용자/비밀번호를 별도 환경 변수로 설정하고 출시 전 권한을 검증한다.

## 근거

- [PostgreSQL 버전 지원](https://www.postgresql.org/support/versioning/)
- [PostgreSQL Windows 바이너리 안내](https://www.postgresql.org/download/windows/)
- [pgJDBC TLS](https://jdbc.postgresql.org/documentation/ssl/)
