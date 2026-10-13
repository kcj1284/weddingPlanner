# Quickstart Validation

Java 21, Maven과 PostgreSQL 17을 준비한다. Windows는 scripts/setup-postgres.ps1, start-postgres.ps1, test.ps1, run.ps1 순서로 실행한다. 상세 안내는 [PostgreSQL 문서](../../docs/postgresql.md)를 참고한다.

1. 호스트 체험 → 기존 이관 모임 확인 → 새 모임과 시간/장소 후보 추가.
2. 초대 대상별 6자리 코드 발급 → 로그아웃 → 초대받은 모임에서 코드 입력 → 이름만 입력해 무가입 참여.
3. 복수 투표 저장 → 같은 코드 또는 링크로 재접속 → 기존 이름·선택 유지 확인.
4. 게스트 장소 찾기·확정 요청 403, 비구성원 조회 403, CSRF 미포함 변경 403.
5. 호스트 확정 → 게스트 투표 409 → 재개 후 기존 표 수정 가능.
6. 키 없는 장소 검색 안내와 수동 후보 추가. 키 설정 후 실제 검색은 별도 검증.
7. 앱/DB 재시작 후 모임·투표 유지, 한국 시간 표시, 모바일 반응형 확인.
8. 실제 PostgreSQL 통합 테스트에서 코드 충돌 후 재시도, NULL/빈 설명 중복, Flyway 적용 확인.

운영은 DATABASE_URL/USERNAME/PASSWORD를 환경 변수에 설정하고 postgres 프로필과 HTTPS를 사용한다. 클라우드 실제 접속·외부 키·Docker 빌드 검증은 아직 별도 환경이 필요하다.
