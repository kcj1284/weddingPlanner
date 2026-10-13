# Render Free + Neon Free 배포

2026-10-13 사용자 선택에 따라 Cloudflare 유료 Containers 대신 Render Free와 Neon Free를 사용한다. **현재 배포 진행 중이며 외부 서비스 URL은 아직 생성하지 않았다.** Cloudflare는 mixdesk.org의 DNS를 담당한다.

## DB 생성

Neon 플러그인으로 새로운 청모 전용 프로젝트를 생성했다. 기존 DB를 복사하거나 수정하지 않았다.

| 항목 | 값 |
|---|---|
| 이름 | cheongmo-production |
| 프로젝트 ID | sweet-breeze-10059904 |
| PostgreSQL | 17 |
| 지역 | aws-ap-southeast-1 (싱가포르) |
| DB / 역할 | cheongmo / cheongmo_app |
| 기본 분기 | production |
| 컴퓨트 | 최소·최대 0.25 CU |
| 계정 | free_v3 확인 |

프로젝트 ID는 접속 비밀번호가 아니다. DB 연결 자격 정보는 공개 문서에 기록하지 않는다. 무료 계정에서는 휴면 간격 변경이 거절되어 기본값을 유지했다. 실제 compute endpoint 휴면 값은 연결 정보를 조회할 때 확인해야 한다.

## 코드 준비

- 기존 Dockerfile로 Render에서 Java 21 앱을 빌드한다. render.yaml은 free·싱가포르·수동 배포·postgres 프로필을 지정한다.
- Brevo HTTPS transactional API를 추가했다. 운영은 MAIL_TRANSPORT=brevo, BREVO_API_KEY, MAIL_FROM을 사용한다. 기존 SMTP와 개발 메일함은 유지한다. API 키가 없으면 로컬 메일함으로 자동 우회하지 않는다.
- Spring Session JDBC와 Flyway V8을 추가했다. postgres 프로필에서는 DB에서 세션을 읽고 저장한다. local/test 기본 흐름은 기존 메모리 세션을 사용한다. 운영 만료 세션 정리는 6시간마다 수행한다.
- HTTPS·Secure 쿠키와 DB TLS 인증서·호스트 검증을 유지한다. 운영에서 예시 계정을 생성하지 않는다.
- 512MB 인스턴스를 고려해 Java 직렬 GC·스레드 스택·메타스페이스·코드 캐시를 제한한다. 실제 Render 시작과 사진 편집 부하는 원격 배포 후 확인한다.

## 운영 설정

필수 Render 환경 변수는 DATABASE_URL(JDBC 형식), DATABASE_USERNAME, DATABASE_PASSWORD, INVITE_CODE_ENCRYPTION_KEY(32바이트 Base64 고정 키), MAIL_TRANSPORT=brevo, BREVO_API_KEY, MAIL_FROM, PUBLIC_BASE_URL=https://mixdesk.org이다.

카카오 검색·공유, 네이버 검색, 소셜 OAuth 키는 필요한 기존 연동을 별도로 등록한다. 카카오 운영 도메인·OAuth redirect URI도 변경해야 한다. 비밀은 Render 환경 변수와 로컬 DPAPI 저장소에만 보관한다. scripts/set-brevo-api.ps1은 숨긴 입력으로 API 키를 암호화 저장한다. SMTP 키를 API 키로 재사용하지 않는다.

## 무료 제한

[Render 공식 제한](https://render.com/docs/free): 15분 무요청 시 휴면, 재기동 지연, 512MB 자원 제한, SMTP 25/465/587 차단. 월 사용량·전송량·빌드 한도를 넘으면 정지 또는 계정 결제 설정에 따른 비용이 발생할 수 있다. 무료 서버에는 가용성 보장이 없다. Render 자체 무료 PostgreSQL의 30일 만료 대신 Neon을 사용한다.

[Neon 공식 무료 안내](https://neon.com/blog/neon-free-plan-1-gb-per-project): 프로젝트당 1GB와 월 100 CU-hours. 사진을 포함한 청첩장 데이터와 DB 세션도 저장량에 포함된다. 휴면 중에는 Java 예약 작업과 메일 큐가 실행되지 않는다. 도메인 갱신료는 기존 Cloudflare 구매 계약에 따른다.

## 검증과 진행 상태

전체 자동 테스트 66개 통과. Brevo HTTP 요청·실패·키 누락, JDBC 세션 저장·새 요청 복구·로그아웃·비밀번호 버전 변경 시 차단을 검증했다. 별도 테스트 DB에서 힙 256MB·제한된 JVM으로 앱을 실행하고 브라우저 로그인 후 Java 프로세스를 실제 중지·재시작했다. 기존 쿠키로 다시 로그인하지 않고 호스트 화면이 복구됐다. 이때 Windows 작업 집합 약 256MB, private memory 약 389MB였으며 Linux 512MB 컨테이너 부하 검증을 대체하지 않는다. 기존 로컬 서비스는 localhost:8080에서 유지한다.

실제 Render·Neon 연결, 도메인·TLS, 실제 Brevo API 메일 수신은 아직 검증하지 않았다. 향후 결과를 이 문서에 갱신한다.

## 2026-10-14 재개

Render 브라우저 로그인에 성공했다. 공개 GitHub 저장소를 연결하고 Free($0/month)·Docker·Singapore·수동 배포·health check `/` 및 공개 환경 변수를 신규 서비스 입력 화면에 준비했다. 아직 Deploy를 실행하지 않았고 서비스 URL도 생성되지 않았다. 배포 소스는 `codex/render-free-neon` 브랜치이며, 관련 코드와 개발 내역을 GitHub에 업로드했다.

현재 Render/Neon 계정 도구가 세션에 노출되지 않아 브라우저로 진행한다. Neon은 로그인 화면이므로 사용자 로그인을 요청했다. 로그인 후 기존 cheongmo-production 프로젝트의 접속 정보를 조회하여 운영 환경 변수에 연결해야 한다. Brevo API 키도 아직 없으므로 메일 연동과 실제 수신 검증은 남아 있다. 기존 SMTP 키로 대체하지 않는다. Cloudflare DNS는 아직 변경하지 않았다.

이름 변경 후 자동 테스트 66개, JS 구문 검사, 파일명 검사, 로컬 재빌드·HTTP 200·호스트 로그인·모임 관리 화면을 확인했다. 로컬 DB는 scripts/connect-db.ps1로 접속하며, scripts/inspect_db.sql로 17개 테이블과 마이그레이션 V1~V8 조회에 성공했다.

