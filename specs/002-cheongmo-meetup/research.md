# Research: 청모

## 서버 및 화면

Decision: Java 21, Spring Boot 4.1.1, Maven, 순수 JavaScript.
Rationale: 사용자의 기존 기술 경험, 모바일 웹에 필요한 규모, 단일 배포의 유지보수 편의.
Alternatives considered: React SPA는 이번 규모에서 별도 빌드·서버 구성 필요성이 없다. Node 서버는 기존 경험을 활용하지 못한다.
Source: https://docs.spring.io/spring-boot/system-requirements.html

## 저장

Decision: JdbcTemplate, PostgreSQL 17 로컬/운영 프로필과 Flyway.
Rationale: 투표 유일 제약, 회원 관계, 모임 잠금을 명시적으로 관리한다. 로컬 PostgreSQL에서 실제 DB 동작을 검증하고 관리형 PostgreSQL에 같은 SQL을 적용한다.
Alternatives considered: 브라우저 저장소는 여러 사용자 공유와 서버 권한을 보장하지 못한다.
Source: https://www.oracle.com/database/technologies/faq-jdbc.html

## 장소 및 예약

Decision: Kakao Local 식당 FD6 / 카페 CE7 검색. 전화·지도 상세 링크 및 호스트 확인 상태 제공.
Rationale: 공식 응답은 장소 정보이며 예약 시간별 잔여석을 제공하지 않는다. key 미설정시 수동 입력으로 이어진다.
Alternatives considered: 예약 제공자 직접 통합은 계약·권한·자격 정보가 필요해 후속 범위로 명시한다.
Source: https://developers.kakao.com/docs/ko/local/dev-guide

## 인증 및 동시성

Decision: BCrypt 계정, 서버 세션, CSRF 토큰. 동일 모임 행 잠금 후 투표·후보·확정 변경.
Rationale: 클라이언트 역할 변조·다른 모임 후보 투표·확정과 투표 경쟁을 차단한다.
Alternatives considered: 역할 선택만으로 인증하는 데모는 실제 호스트 권한을 보호하지 못한다.
