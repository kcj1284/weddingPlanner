# DB 직접 조회

현재 개발 DB는 로컬 PostgreSQL이다. 이미 설치된 psql을 사용하므로 별도 설치나 비밀번호 복사가 필요 없다.

PowerShell에서 프로젝트 폴더를 열고 실행한다.

```powershell
cd C:\weddingPlanner
.\scripts\connect-db.ps1
```

접속 후 `\dt`는 테이블 목록, `\d meeting`은 테이블 구조, `\q`는 종료다. SQL은 세미콜론으로 끝낸다.

```sql
SELECT id, title, area, status, created_at FROM meeting ORDER BY created_at DESC LIMIT 20;
SELECT id, username, display_name FROM app_user LIMIT 20;
```

한 번에 상태를 확인하거나 테스트 DB로 접속할 수도 있다.

```powershell
.\scripts\connect-db.ps1 -SqlFile .\scripts\inspect-db.sql
.\scripts\connect-db.ps1 -Query 'SELECT current_database(), current_user;'
.\scripts\connect-db.ps1 -Database test
```

접속 주소는 127.0.0.1, DB와 사용자는 cheongmo(테스트는 cheongmo_test)다. 실제 포트와 비밀번호는 Git에서 제외된 data/local-postgres.json에서 읽는다. 스크립트는 비밀번호를 출력하지 않고 실행 후 환경 변수를 복원한다.

기본 읽기 전용 트랜잭션으로 접속한다. 별도의 읽기 전용 역할은 아니므로 사용자가 직접 설정을 해제하면 쓰기가 가능하다. 이 문서의 조회 예제는 개인 연락처·인증 비밀 컬럼을 선택하지 않는다.

Neon 운영 DB는 프로젝트 생성만 완료됐으며 접속 정보와 서버 배포는 아직 연결되지 않았다. 이 명령은 운영 DB에 연결하지 않는다.

[PostgreSQL psql 공식 문서](https://www.postgresql.org/docs/current/app-psql.html)
