# Quickstart Validation

Java 21 및 Maven으로 mvn test, mvn spring-boot:run -Dspring-boot.run.profiles=local.
현재 workspace의 .tools 도구를 사용할 때는 scripts/run.ps1과 scripts/test.ps1을 실행한다.
http://localhost:8080 접속. local 프로필에서만 예시 계정 체험 가능.

1. 호스트 체험 → 모임 상세 → 시간/장소 후보 추가 → 초대 링크 복사.
2. 다른 브라우저에서 새 계정 가입 후 링크 참여 → 복수 투표 → 수정 → 집계 확인.
3. 게스트는 장소 찾기·확정 버튼이 없으며 직접 API 호출도 403.
4. 호스트가 날짜/장소 확정 → 게스트 투표 409 → 재개 후 다시 투표 가능.
5. 장소 찾기: key 없음 안내 확인 → 수동 장소 추가. KAKAO_REST_API_KEY 설정시 실제 검색.
6. 360/390/768/1280px에서 넘침·키보드 조작 확인.
7. 서버 재시작 후 로컬 DB의 모임과 투표 유지 확인.

Oracle: ORACLE_URL, ORACLE_USERNAME, ORACLE_PASSWORD 설정 후 schema.sql 검토·최초 수동 적용.
프로필 oracle로 실행. 이 가이드는 Oracle 실제 접속 성공을 보장하지 않는다.
