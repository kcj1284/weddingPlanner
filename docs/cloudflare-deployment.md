# Cloudflare 배포 준비

2026-10-13. **아직 배포되지 않았다.** 연결된 Cloudflare 계정에서 mixdesk.org 활성 상태를 확인했지만 Containers API는 Workers Paid 미가입으로 배포 접근을 거절했다. 구독·결제와 운영 PostgreSQL 준비가 필요하다.

## 배포 구성

mixdesk.org → Cloudflare Worker → Spring Boot Container → 외부 운영 PostgreSQL.

`deploy/cloudflare/wrangler.jsonc`에 연결된 계정, mixdesk.org custom domain, 기존 루트 Dockerfile, basic 인스턴스(1 GiB), 최대 1개를 설정했다. Worker는 Java 앱의 HTML·API를 함께 전달하며 HTTPS와 전달 헤더를 처리한다. Container는 postgres 프로필만 사용한다. localhost DB나 로컬 SMTP·초대 암호화 키를 자동 업로드하지 않는다. 운영 DB를 새로 만들면 기존 개발 데이터를 자동 복사하지 않는다.

핵심 설정이 없으면 Worker는 503 안내만 반환하고 앱을 시작하지 않는다. 운영 DB는 TLS 호스트·인증서 검증을 유지한다. D1은 PostgreSQL과 다른 DB이므로 현재 앱을 D1로 바꾸지 않았다.

## 확인된 계정 상태

- Cloudflare 연결 계정에서 mixdesk.org zone active 확인.
- mixdesk.org 자체의 DNS 레코드와 Worker custom domain 등록 없음 확인. DNS 변경은 수행하지 않았다.
- Containers API: Workers Paid 필요 오류 확인. 도메인 구매는 Workers 구독을 포함하지 않는다.
- PostgreSQL: 사용자 답변에 따라 운영 DB 없음. 앱의 개발 DB는 로컬 전용이다.
- 로컬 Docker 미설치. 컨테이너 이미지 빌드는 아직 수행하지 않았다.

## 비용과 빌드 경로

[Containers 가격](https://developers.cloudflare.com/containers/platform/pricing/)에 따르면 Workers Paid 기본 월 $5에 메모리·CPU·디스크·네트워크 및 관련 Workers/DO 사용량이 추가된다. 최대 인스턴스 1개는 확장 수 제한이며 요금 상한이 아니다. 유료 가입·결제는 계정 소유자가 직접 진행한다.

로컬 Docker 설치 없이 [Workers Builds](https://developers.cloudflare.com/containers/guides/deploy/)에서 Git 저장소를 연결해 기존 Dockerfile을 빌드할 수 있다. Cloudflare 빌드의 root는 저장소 루트로 두고 다음 명령을 사용한다.

```text
Build command: npm --prefix deploy/cloudflare ci && npm --prefix deploy/cloudflare run check
Deploy command: npm --prefix deploy/cloudflare run deploy
```

Worker 이름은 `cheongmo`로 일치시킨다. 저장소의 현재 변경을 Git에 반영해야 원격 빌드가 같은 코드를 사용한다. Git 연결·push·빌드 설정·배포는 아직 수행하지 않았다.

## 운영 PostgreSQL과 Secrets

초기 DB 후보는 관리형 PostgreSQL의 Neon Free이다. [공식 무료 플랜 안내](https://neon.com/blog/neon-free-plan-1-gb-per-project). 가입과 서비스 약관 처리는 사용자가 직접 한다. 연결 후 앱용 운영 DB를 생성하고, 가까운 지역과 PostgreSQL 17을 선택할 수 있는지 콘솔에서 확인한다. 앱의 Flyway V1~V7은 새 운영 DB에 적용된다. 기존 운영 데이터가 있는 DB를 자동 이관 대상으로 사용하지 않는다.

Cloudflare Worker의 runtime Secrets에 등록할 필수 값:

| 이름 | 용도 |
|---|---|
| DATABASE_URL | `jdbc:postgresql://<호스트>:5432/<DB명>` |
| DATABASE_USERNAME | 앱용 DB 사용자 |
| DATABASE_PASSWORD | DB 비밀번호 |
| INVITE_CODE_ENCRYPTION_KEY | Base64로 인코딩한 고정 32바이트 키 |

PUBLIC_BASE_URL은 https://mixdesk.org로 설정했다. SMTP_HOST·SMTP_PORT·SMTP_USERNAME·SMTP_PASSWORD·MAIL_FROM, 카카오 검색·공유 키, 소셜 제공자 키도 runtime Secrets로 연결할 수 있다. 비밀을 저장소·채팅·이미지 빌드 인자로 전달하지 않는다. 카카오·구글·네이버 콘솔의 운영 도메인 및 OAuth redirect URL 등록도 배포 후 필요하다.

## 현재 검증과 제한

TypeScript 검사 통과. Wrangler runtime 타입 생성 완료. `check-worker-build`로 Worker 전용 dry-run 성공(업로드·리소스 변경 없음). Docker를 포함한 전체 dry-run은 Docker 미설치로 실패했으며 이미지 빌드 성공이나 실제 배포를 주장하지 않는다. 기존 Spring Boot 서버는 localhost:8080에서 유지한다. 타입 파일은 생성물로 Git에서 제외하고 `npm run check`가 빌드 환경에서 다시 생성한다.

Container는 15분 요청이 없으면 정지하도록 준비했다. **현재 로그인 세션과 일부 요청 제한은 메모리 기반**이므로 정지·재시작·재배포 시 로그인 세션이 사라지고 다시 로그인해야 한다. 모임·투표·청첩장은 외부 PostgreSQL에 유지된다. 서비스 출시 전 장시간 로그인 유지가 필요하면 JDBC 등 영속 세션을 적용해야 한다. 최대 1개는 실행 중 세션 분산을 방지하는 초기 설정이며 무중단·고가용성 구성이 아니다. 정지 동안 장소 갱신 스케줄과 비동기 메일 작업도 실행되지 않는다.

최종 배포 확인은 Worker URL 응답뿐 아니라 실제 Container 시작·운영 DB 마이그레이션·가입/로그인·게스트 참여·청첩장 공개·메일 발송까지 확인해야 한다.
