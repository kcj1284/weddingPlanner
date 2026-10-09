# HTTP Contracts

Cookie session 인증. 모든 변경 요청은 GET /api/csrf의 headerName 및 token을 전달한다.
오류 응답 {message}. 400 입력 오류, 401 미인증, 403 권한/CSRF, 404 없음, 409 확정/중복/한도.

| Method | Path | Body / Result | 권한 |
| --- | --- | --- | --- |
| GET | /api/csrf | token, headerName | 공개 |
| GET | /api/config | demoEnabled | 공개 |
| POST | /api/register | username,displayName,password | 공개 + CSRF |
| POST | /api/login | form username,password | 공개 + CSRF |
| POST | /api/logout | 세션 종료 | 인증 + CSRF |
| GET | /api/me | id,username,displayName | 인증 |
| GET/POST | /api/meetings | 목록 / title,description,area,expectedCount | 인증 |
| POST | /api/join/{token} | 모임 id | 인증 |
| GET | /api/meetings/{id} | 상세,role,options,myVotes,memberCount,responseCount,inviteToken | 구성원 |
| POST | /api/meetings/{id}/options | kind,title,detail,mapUrl,phone | 호스트 |
| PUT | /api/meetings/{id}/votes | optionIds 배열 최대 60 | 구성원, OPEN |
| POST | /api/meetings/{id}/confirm | timeId,placeId | 호스트, OPEN |
| POST | /api/meetings/{id}/reopen | 없음 | 호스트 |
| PUT | /api/meetings/{id}/options/{optionId}/reservation | status,note | 호스트, PLACE |
| GET | /api/meetings/{id}/places?q=...&type=FD6 | configured,message,places | 호스트 |

장소 검색 결과는 name,address,phone,url. 예약 가능 여부는 포함하지 않는다.
투표 저장 및 상태 변경은 상세를 다시 조회하는 화면에서 표시한다.

## 게스트 링크 / 소셜 인증 추가

| 메서드 | 경로 | 권한 / 결과 |
|---|---|---|
| GET | /api/auth/providers | 공개; kakao/google/naver 활성 상태만 반환 |
| GET | /oauth2/authorization/{provider} | 설정된 제공자의 인증 화면으로 state 포함 리다이렉트 |
| GET | /login/oauth2/code/{provider} | Spring Security 콜백; 첫 로그인 시 호스트 생성 |
| POST | /api/meetings/{id}/guest-links | 호스트 + CSRF; {label} → {id,token,code,label,expiresInDays:30} |
| GET | /api/meetings/{id}/guest-links | 호스트; label,displayName,revoked,expiresAt 메타데이터; 토큰 원문 없음 |
| DELETE | /api/meetings/{id}/guest-links/{linkId} | 호스트 + CSRF; 권한과 기존 투표 제거 |
| GET | /api/guest-preview/{token} | 공개; {title,joined}; 잘못되거나 만료/취소된 토큰 404 |
| POST | /api/guest-session | 공개 + CSRF; {token,displayName}; 첫 참여는 이름 필수. 세션 교체 → {meetingId} |

게스트 진입 후 /api/csrf를 다시 요청한다. /api/me의 guest boolean으로 게스트 화면을 구성한다. 멤버십 검사는 매 요청 수행하므로 취소 전 세션도 해당 모임 접근이 403이 된다. 게스트에게 inviteToken을 반환하지 않는다.

신규 guest-links 응답의 token/code는 동일한 6자리 대문자 영문·숫자 코드다. 공개 guest-preview와 guest-session은 코드도 받으며 IP당 분당 30회 초과 시 429를 반환한다. 기존 긴 토큰도 허용한다.
