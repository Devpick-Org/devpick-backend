# API 갭 매트릭스 (배포 전 계약 정합)

배포 전 **구현·OpenAPI·보안·빈 응답**을 한눈에 맞추기 위한 요약이다. 상세는 `devpick-api-spec.json`과 컨트롤러가 정본이다.

## 원칙

- **Contract-first**: DTO 변경 시 같은 PR에 OpenAPI 반영.
- **Compatibility-first**: 스펙에만 있는 경로는 구현 또는 alias로 채움.
- **No-empty**: 목록은 `[]`, 카운트는 `0`, 페이지 구조는 유지.

## 도메인별 표면

| 영역 | 구현 기준 | OpenAPI | 비고 |
|------|-----------|---------|------|
| Health | `GET /api/health`, `GET /health` | 둘 다 문서화 | 게이트웨이 경로 호환 |
| Auth/User | `AuthController`, `UserController` | `SocialLoginResponse`, `SignupRequest` 약관 필드 | 소셜 콜백 응답 타입 통일 |
| Point | `GET /users/me/points*`, `GET /users/me/badges` | paths + 스키마 | `/history/points`는 별칭 가능 |
| Content | 피드·검색·상세·요약·퀴즈 | `ContentSummaryResponse` 등 최신 필드 | |
| Community | `GET /posts?query=`, 답변·유사·AI답변·좋아요 | 누락 path 보강 | 읽기 일부 공개 |
| History | `GET /history`, `GET /history/activity` | 학습 vs 활동 구분 | 학습은 `content_liked` 제외 |
| Report | `GET /reports/weekly` 온디맨드 생성 | “없으면 생성” 설명과 일치 | |
| Trend | `GET /trends/keywords` | 스펙 추가 | |

## 공개 vs 인증 (요약)

- **공개**: 헬스(`/`·`/api`), 게시글 목록·상세·답변·유사질문 GET, 타인 프로필 GET, 공유 리포트, `GET /trends/keywords`.
- **인증**: 콘텐츠 피드·검색·상세(`@AuthenticationPrincipal` 필요), `/users/me/**`, 쓰기 전부, AI 요약/퀴즈/추천, 히스토리, 주간 리포트(본인), 포인트.

## 검색

- 콘텐츠: `GET /contents/search`
- 커뮤니티: `GET /posts?query=` (제목·본문 부분 일치)
