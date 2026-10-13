# Implementation Plan: 청모 모바일 모임 조율

**Branch**: 없음 | **Date**: 2026-10-08 | **Spec**: [spec.md](spec.md)
**Input**: specs/002-cheongmo-meetup/spec.md

## Summary

Spring Boot 단일 서버에서 JavaScript 모바일 화면과 REST 기능을 제공한다.
서버 세션으로 인증하고 모임별 권한을 검사한다. 트랜잭션과 모임 행 잠금으로 투표·확정 충돌을 제어한다.

## Technical Context

**Language/Version**: Java 21, JavaScript ES modules, HTML, CSS
**Primary Dependencies**: Spring Boot 4.1.1, MVC, Security, JDBC, Validation, RestClient
**Storage**: PostgreSQL 17, Flyway 버전 관리; 로컬 앱 DB/테스트 DB 분리
**Testing**: 실제 PostgreSQL JUnit 통합 테스트, 브라우저 사용자 여정 검증
**Target Platform**: 모바일·데스크톱 웹, Java 서버
**Project Type**: 단일 웹 애플리케이션
**Performance Goals**: 작은 모임 2–100명에서 정상적인 목록·투표 응답
**Constraints**: CSRF 방어, 서버 권한 검사, 외부 key 서버 전용, 예약 재고 조회 제외
**Scale/Scope**: 모임 생성·초대·일정/장소 투표·장소 검색·예약 상태 기록·확정 및 재개

## Constitution Check

- I: 새 명세와 인수 조건을 작성하고 기존 웨딩 준비 문서는 보관한다.
- II: 인증된 구성원만 접근한다. 계정 복구·삭제·보존 자동화는 명세에서 MVP 후속 범위로 명시했다. 출시 전에 보완해야 한다.
- III: 시간은 한국 시간, 투표는 원자적 교체, 투표와 확정은 동일 모임 잠금으로 보호한다.
- IV: 명세에 권한·집계·확정·검색 실패 자동 검증을 요구했다.
- V: 단일 배포와 순수 JS로 복잡도를 제한한다. 키보드·모바일·오류 상태를 검증한다.
- Phase 1 재검토: 동일 통과. PostgreSQL 로컬 실제 연결을 검증한다. 외부 관리형 DB 및 검색 성공은 자격 정보가 필요하다.

## Project Structure

### Documentation (this feature)

specs/002-cheongmo-meetup/: spec.md, plan.md, research.md, data-model.md, quickstart.md, contracts/http.md, tasks.md

### Source Code (repository root)

- pom.xml, scripts/: 빌드·실행
- src/main/java/kr/cheongmo/: 계정·보안·모임·장소 검색
- src/main/resources/: 프로필, schema.sql, static/index.html, static/app.js, static/styles.css
- src/test/java/kr/cheongmo/: 통합 검증

**Structure Decision**: 별도 프런트엔드 빌드 없이 Spring Boot가 정적 화면을 제공한다.

## Complexity Tracking

MVP 후속 운영 기능은 명세 범위에 명시했다. 실제 예약 재고 연동은 공급자 계약과 별도 명세가 필요하다.
로컬 H2 검증은 Oracle 실환경 검증을 대체하지 않는다.

추가 인증 범위: Spring Security OAuth2 Client로 카카오·구글·네이버 호스트 로그인, 게스트는 해시 토큰에 연결된 내부 참여자와 ROLE_GUEST 세션으로 구현한다. 연동 설정 및 검증 제한은 docs/social-login.md에 기록한다.

## PostgreSQL 전환 결정 (2026-10-12)

사용자가 비용 검토 후 PostgreSQL을 허용했다. Java 21과 JDBC/JS를 유지하고 PostgreSQL 드라이버·DDL·COALESCE·ON CONFLICT 재시도를 사용한다. Flyway 의존성은 DB 스키마 변경의 추적과 검증을 위해 추가한다. 실제 PostgreSQL 테스트 DB를 앱 DB와 분리한다. 기존 H2는 읽기 전용 이관과 전체 내용 비교 후 보존하고 새 DB가 비어 있지 않으면 이관을 거부한다. 로컬 원본 백업과 이전 앱 커밋으로의 복구 방법은 docs/postgresql.md에 기록한다. 운영 전환 이후의 신규 데이터 롤백은 별도 이관 절차가 필요하다. 비용 우선의 초기 배포 후보는 관리형 PostgreSQL 무료 플랜이며, 실제 이용자를 받기 전 백업·복원과 유료 예산을 확인한다.
