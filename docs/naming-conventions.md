# 이름 규칙

각 언어와 파일의 역할에 맞는 규칙을 적용한다.

| 대상 | 규칙 | 예 |
|---|---|---|
| Java 클래스·파일 | PascalCase, public 클래스와 파일명 일치 | PlaceController.java |
| Java 패키지 | 소문자, 기능별 분리 | kr.cheongmo.invitation |
| Java 변수·메서드 | lowerCamelCase, 역할을 드러내는 이름 | accountService, authenticatedPrincipal |
| Java 상수 | UPPER_SNAKE_CASE | CODE_ALPHABET |
| Java 계층 | 단수 도메인 + Controller/Service/Repository | MeetingRepository |
| Java DTO 묶음 | 도메인 + Dtos, 내부 record는 PascalCase | AccountDtos.Registration |
| JS 파일·HTML·CSS | kebab-case | invitation-address.js |
| JS 변수·함수 | lowerCamelCase | csrfToken, dateLabel |
| JS 클래스·생성자 | PascalCase | Designer, Postcode |
| JS 고정 상수 | UPPER_SNAKE_CASE | FONT_OPTIONS |
| HTML id·CSS 클래스 | kebab-case | auth-form |
| HTML data 속성 | kebab-case, JS dataset은 camelCase | data-design-control / designControl |
| SQL 테이블·컬럼 | snake_case | app_user, display_name |
| Flyway 파일 | V번호__snake_case.sql | V8__persistent_sessions.sql |
| Python 파일·함수·변수 | snake_case | generate_newspaper.py |
| PowerShell 파일 | kebab-case.ps1 | connect-db.ps1 |
| PowerShell 변수 | lowerCamelCase, 환경 변수는 UPPER_SNAKE_CASE | databaseSettings, DATABASE_URL |
| PowerShell 함수 | 승인된 Verb-Noun | Protect-LocalFile |
| 문서 | kebab-case.md | naming-conventions.md |

인덱스·반복문의 i, j, 좌표 x, y, SQL 별칭처럼 의미가 분명한 짧은 이름은 허용한다. HTTP/SQL 외부 계약과 이미 적용된 Flyway 파일은 이름만을 위해 변경하지 않는다. Spring Session의 공식 스키마 이름, Dockerfile, README.md, CONTEXT.md 등 도구가 정한 이름도 유지한다.

컨트롤러의 축약 입력·인증·요청 변수와 주입 의존성을 정리하고, PlacesController를 PlaceController로 변경했다. Repository의 db는 jdbcTemplate, 직렬화 도구 json은 objectMapper, 환경 설정 env는 environment로 명확히 했다. JS 공통 함수 esc/url은 escapeHtml/safeHttpUrl, 고정 예약 상태 표는 RESERVATION_LABELS로 변경했다. Python 생성 스크립트는 generate_newspaper.py로 변경했다. API URL 및 JSON 이름은 유지한다.
