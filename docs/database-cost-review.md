# 청모 DB 및 배포 비용 검토

검토일: 2026-10-12. 신규 소규모 서비스, 초기 낮은 트래픽, 별도 Oracle 운영 라이선스가 없는 경우를 기준으로 비교한다. 아래 금액은 미국 달러 기준 공식 공개 요금이며 계약 견적이나 실제 사용료 보장이 아니다. 세금·도메인·사용량 초과·전송·추가 백업 비용은 별도다.

## 선택 결정

사용자가 PostgreSQL 전환을 허용했고 2026-10-12 로컬 PostgreSQL 전환을 완료했다. 아래 표는 결정 전 비교이며 현재 실행 방법은 [PostgreSQL 안내](postgresql.md)를 참고한다.

## 결론

Oracle 19c 경험을 유지하면서 초기 비용을 줄이려면 OCI Always Free Autonomous AI Database의 19c 선택지를 먼저 확인하는 것이 적합하다. 반면 유료 운영 단계에서도 라이선스 부담을 줄이고 선택지를 넓히는 것이 최우선이면 Spring Boot와 JavaScript를 유지하고 PostgreSQL을 사용하는 방향을 추천한다.

19c라는 버전 자체가 비용을 결정하지 않는다. 제공 방식, 라이선스 포함 여부, 인스턴스·저장 공간, 백업·가용성 구성이 비용을 결정한다. Always Free와 30일 무료 체험 크레딧은 구분해야 한다.

## 후보 비교

| 후보 | DB 비용 출발점 | 현재 코드 영향 | 운영에서 확인할 점 |
|---|---|---|---|
| OCI Always Free Autonomous AI Database 19c | Always Free 범위 내 $0 | 작음: 원격 연결, TLS/Wallet, 권한과 스키마 검증 | 홈 리전·생성 가능 여부, 비활성 자동 중지, 복원 기능 제한 |
| 관리형 Oracle 19c 유료 서비스 | 리전·크기·에디션별 견적 | 작음: 연결·운영 설정 및 실제 19c 검증 | 라이선스 포함 상품 또는 적법한 BYOL, 백업·가용성·저장 비용 |
| Neon 관리형 PostgreSQL | Free 범위 내 $0, 유료는 사용량 기반 | DDL·SQL·충돌 처리·테스트 수정 | 무료 할당량, 휴면 후 재기동 지연, 복원 보존 기간, 사용량 예산 |
| Supabase PostgreSQL | Free $0 / Pro 월 $25부터 | 위와 같은 PostgreSQL 전환 범위 | 무료 500MB 및 비활성 중지, Pro 기본 프로젝트·초과 요금 |

OCI 공식 문서는 Always Free에서 19c와 26ai를 선택할 수 있다고 안내한다. 기본 버전은 변경될 수 있으므로 19c를 명시적으로 선택해야 한다. 제공 가능 여부는 가입한 홈 리전과 실제 콘솔에서 확인한다.

Always Free DB는 7일 비활성 시 자동 중지되고, 중지/비활성이 누적 90일 지속되면 회수·삭제될 수 있다. 무료 서비스는 과거 시점으로 복원하는 기능을 제공하지 않으며 유료 전환 시 해당 기능을 사용할 수 있다. 사용자 데이터를 받기 전에는 별도 데이터 내보내기·복구 절차를 검증해야 한다. 이를 영구 무비용 운영 보장으로 해석하지 않는다.

Amazon RDS for Oracle의 License Included 모델에는 SE2 라이선스가 포함된다. BYOL은 이미 적절한 라이선스 및 지원 자격을 갖춘 경우의 선택지이며 별도의 라이선스 비용이 없다고 가정하면 안 된다. 정확한 요금은 목표 리전과 인스턴스, 저장 공간, 가용성 구성을 정한 뒤 계산한다.

Neon의 유료 플랜은 최소 월 구독료 없이 사용량에 따라 과금하는 선택지를 제공한다. 무료 플랜과 유료 플랜의 용량·복원 한도는 가입 시 최신 요금표에서 재확인한다. Supabase Pro는 기본 Micro 프로젝트 1개에 해당하는 컴퓨팅 크레딧을 포함해 월 $25부터 시작하며, 추가 프로젝트·컴퓨팅·사용량은 별도다. Supabase Free는 비활성 1주 후 중지될 수 있다.

## 서버까지 포함한 예시

Render Starter 서버는 월 $7, 512MB 메모리의 공개 요금 선택지다. 이 크기에서 청모 Spring Boot 애플리케이션의 JVM 메모리와 동시 요청을 아직 측정하지 않았으므로 최종 서버 크기로 확정할 수 없다.

- Render Starter + 무료 DB: 무료 DB 할당량 안에서는 기본 요금 월 $7부터 시작하는 예시다.
- Render Starter + Supabase Pro 기본 프로젝트: 기본 요금 합계 월 $32부터 시작하는 예시다.
- 메모리가 부족해 Render Standard(월 $25, 2GB)를 쓰면 Supabase Pro와 기본 합계는 월 $50부터다.

이는 앱 서버와 DB 기본 요금만 합산한 값이다. 실제 청구에는 해당 서비스의 사용 조건, 대역폭·초과 사용량·세금 등이 영향을 준다. 앱과 DB의 리전을 가깝게 배치하고 외부 DB 접근 지연도 검증해야 한다.

## 코드 검토 결과

현재 데이터 접근은 Spring JDBC이고 주요 기능은 관계형 CRUD와 행 잠금이다. 프로시저나 Oracle 패키지에 의존하지 않아 PostgreSQL 전환 시 화면과 서비스 기능의 대부분을 유지할 수 있다. 단, DB 드라이버 교체만으로 완료되지는 않는다.

- VARCHAR2/NUMBER 등 DDL 타입과 Oracle 전용 NVL 수정.
- Oracle의 빈 문자열=NULL 규칙과 PostgreSQL의 빈 문자열 처리 차이 반영.
- 현재 초대코드 중복 시 INSERT 예외를 잡아 재시도하는 방식 수정. PostgreSQL에서는 제약 위반 뒤 같은 트랜잭션을 계속 사용할 수 없으므로 ON CONFLICT 또는 별도 충돌 처리 설계가 필요하다.
- 실제 PostgreSQL에서 스키마, 가입·투표·동시 확정·초대 재접속·중복 발급 테스트 수행.

Oracle을 선택하는 경우에도 현재 자동 테스트는 H2 Oracle 호환 모드 기반이다. 실제 Oracle 19c 검증을 추가해야 한다.

## 비용을 낮추는 실행 순서

1. Oracle 숙련도 유지가 필수이면 OCI 19c Always Free의 생성 가능 여부를 확인하고 연결·복구 절차를 검증한다.
2. 장기 저비용 운영이 우선이면 사용자 데이터가 쌓이기 전에 PostgreSQL 전환을 결정한다.
3. 초기에는 단일 앱 서버와 단일 DB 프로젝트를 사용한다. Redis, Kubernetes, 다중 서버를 선행 도입하지 않는다.
4. 실제 메모리·접속 수·저장량·요청량을 측정하고 비용 알림과 지원되는 예산 제한을 설정한다. 알림 자체가 과금을 강제로 막는다고 가정하지 않는다.
5. 초대와 투표 데이터를 실제로 받는 출시 단계에서는 백업·복원 검증을 갖추고 필요한 유료 플랜 예산을 확보한다.

## 공식 근거

- [OCI Always Free Autonomous AI Database](https://docs.oracle.com/en/cloud/paas/autonomous-database/serverless/adbsb/autonomous-always-free.html)
- [OCI 신규 Autonomous DB 기본 버전 변경](https://blogs.oracle.com/autonomous-ai-database/default-database-version-changing)
- [Amazon RDS for Oracle 요금과 라이선스](https://aws.amazon.com/rds/oracle/pricing/)
- [PostgreSQL 라이선스](https://www.postgresql.org/about/licence/)
- [Neon 요금 플랜 공식 문서 소스](https://github.com/neondatabase/website/blob/main/content/docs/introduction/plans.md)
- [Supabase 요금](https://supabase.com/pricing)
- [Supabase 비활성 중지 정책](https://supabase.com/docs/guides/platform/free-project-pausing)
- [Render 공식 서버 요금 비교](https://render.com/articles/render-vs-railway)
