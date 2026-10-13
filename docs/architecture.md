# 개발 구조

2026-10-13 기능별 MVC 계층 분리. HTML·JavaScript 화면과 REST API를 유지한다.

## 요청 흐름과 책임

```text
HTML / JavaScript View
    → REST Controller
    → Service (업무 규칙·권한·트랜잭션)
    → Repository (SQL·DB 결과 변환)
    → PostgreSQL
```

| 계층 | 담당 | 두지 않는 코드 |
|---|---|---|
| View | 화면 생성, 입력 안내, 미리보기 | SQL, 서버 권한 판단 |
| Controller | URL·입력 검증·인증 사용자·HTTP 응답 | SQL, 메일 작업 큐 |
| Service | 모임·투표·초대·계정 복구 규칙과 트랜잭션 | JdbcTemplate, Controller 의존 |
| Repository | SQL, 잠금 조회, 저장, DB 행 변환 | HTTP 요청·응답, 화면 문구·역할 흐름 |
| DTO | 요청·응답의 이름과 타입, 입력 제약 | DB 접속·업무 처리 |

현재 예외는 공통 API 오류 처리로 연결되는 `ResponseStatusException`을 유지한다. 보안 필터·OAuth·메일·암호화·초기 데이터는 해당 기능 또는 config의 별도 컴포넌트다. Spring Security의 세션 연결은 HTTP 계층에서 처리한다.

## 백엔드 위치

```text
src/main/java/kr/cheongmo/
├─ CheongmoApplication.java
├─ account/
│  ├─ AccountController.java
│  ├─ AccountService.java
│  ├─ AccountRepository.java
│  ├─ AccountRecoveryController.java
│  ├─ AccountRecoveryService.java
│  ├─ AccountRecoveryRepository.java
│  ├─ AccountRecoveryMailService.java
│  ├─ RecoveryMailService.java
│  ├─ SocialLoginController.java / SocialLoginService.java
│  ├─ AccountVersionFilter.java
│  └─ dto/AccountDtos.java / RecoveryDtos.java
├─ meeting/
│  ├─ MeetingController.java / MeetingService.java / MeetingRepository.java
│  └─ dto/MeetingDtos.java
├─ guest/
│  ├─ GuestAccessController.java / GuestAccessService.java / GuestAccessRepository.java
│  ├─ GroupInviteController.java / GroupInviteService.java / GroupInviteRepository.java
│  ├─ GuestAttemptLimiter.java / InviteCodeCipher.java
│  └─ dto/GuestDtos.java / GroupInviteDtos.java
├─ invitation/
│  ├─ InvitationController.java / InvitationService.java / InvitationRepository.java
│  ├─ QrController.java
│  └─ dto/InvitationDtos.java
├─ place/
│  ├─ PlaceController.java / PlaceService.java
│  ├─ PlaceDiscoveryService.java / PlaceDiscoveryRepository.java
│  └─ dto/PlaceDtos.java
├─ common/CredentialPolicy.java / ApiErrors.java
└─ config/SecurityConfig.java / DemoData.java
```

모든 Controller를 별도 파일로 분리했다. 7개 Repository에 SQL과 결과 매핑을 옮겼다. Service의 트랜잭션 위치와 PostgreSQL 행·advisory 잠금, 초대 충돌 재시도, 낙관적 버전 검사 및 토큰 사용 처리는 유지한다. 로컬 초기 데이터는 config에 있으며 운영 프로필에서 실행되지 않는다.

계정, 계정 복구, 모임 목록·상세·후보, 청첩장 목록·본문의 고정 API 응답은 명시적 record DTO를 사용한다. 요청 record도 dto로 옮겼다. 게스트·단체 초대의 기존 응답, 내부 DB 행과 외부 검색·캐시의 유동적인 데이터에는 Map이 남아 있다. 서비스 공개 인터페이스 전체를 도메인 모델로 바꾼 구조는 아니다.

## 프런트엔드 위치

```text
static/
├─ index.html / app.js (청모 화면 흐름·이벤트 진입점)
├─ account.html / account.js (계정 복구)
├─ invitation.html / invitation.js (청첩장 편집·공개 화면)
├─ js/shared/api-client.js (공통 API·CSRF·오류 처리)
├─ js/shared/view-helpers.js (출력 이스케이프·URL·표시 유틸)
├─ js/meeting/meeting-state.js (청모 상태)
├─ js/meeting/meeting-view.js (청모 화면 렌더링)
├─ js/account/auth-view.js (가입·로그인 화면)
└─ invitation-*.js (자유 편집·주소·계좌·신문형·단계 안내)
```

세 진입 화면이 API 클라이언트를 공유한다. 상태와 View는 진입점에서 명시적으로 연결하며 이벤트·요청 순서는 진입점이 관리한다. 신규 화면에서는 공통 API 클라이언트를 사용하고 화면이 커지면 기능별 View 모듈로 나눈다. JSP·Thymeleaf·별도 프런트엔드 프레임워크는 추가하지 않았다.

## 기능을 추가할 때

1. 해당 기능의 dto에 요청·응답 타입을 정의한다.
2. Repository에 이름 있는 조회·저장 메서드를 추가한다. 여러 쓰기의 트랜잭션은 Service에 둔다.
3. Service에서 역할·업무 규칙을 검사하고 Repository를 호출한다.
4. Controller는 사용자·입력을 전달하고 DTO를 반환한다.
5. 화면 모듈에서 API를 호출하고 필요한 상태·View를 갱신한다.

전체 리팩터링 검증 결과는 [검증 기록](structure-refactor-20261013.md)에 기록한다. `ArchitectureBoundaryTest`는 Controller의 Repository/JDBC 직접 의존과 Service의 JDBC/Controller 의존을 금지한다.

