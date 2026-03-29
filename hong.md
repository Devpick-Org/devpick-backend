# Trace 백엔드 — 프로젝트 현황 문서

> **마지막 업데이트**: 2026-03-29
> **기준 브랜치**: develop

---

## 1. 프로젝트 개요

| 항목 | 내용 |
|------|------|
| **프로젝트명** | Trace: 콘텐츠 큐레이션 기반 개발자 성장 플랫폼 |
| **레포** | https://github.com/Devpick-Org/devpick-backend |
| **Base URL** | https://api.devpick.kr/v1 |
| **MVP 데드라인** | 2026-04-13 |
| **캡스톤 발표** | 2026-06-04 |

### 팀 구성

| 이름 | 역할 | 담당 |
|------|------|------|
| **홍근** | PM / 백엔드 리드 | 전체 아키텍처, Epic A~F API, CI/CD, Jira/Confluence 운영 |
| **하영** | 백엔드 서브 | 콘텐츠 수집 파이프라인, 인증 API 일부, 커뮤니티 API 일부 |
| **수헌** | AI 엔지니어 | RAG 파이프라인, FAISS, 프롬프트 엔지니어링, 유사 질문 |
| **보민** | 프론트엔드 | Next.js UI/UX 전체 |

### 기술 스택

| 구분 | 기술 | 버전 |
|------|------|------|
| 언어 | Java | 21 (LTS) |
| 프레임워크 | Spring Boot | 3.5.11 |
| ORM | JPA/Hibernate + QueryDSL | 5.1.0:jakarta |
| DB (구조화) | PostgreSQL | 16 (AWS RDS) |
| DB (비정형) | MongoDB | 7 |
| 캐시 | Redis | 7 |
| AI 서버 | FastAPI + Python | 3.12 |
| CI/CD | GitHub Actions + SonarCloud | — |
| 인프라 | Docker + AWS EC2 | — |

---

## 2. Epic 완료 현황

### ✅ 1단계 MVP (Epic A~F) — 완료

| Epic | 기능 | 포함 티켓 | 상태 |
|------|------|-----------|------|
| **A. 회원/프로필** | 이메일+소셜(GitHub/Google) 로그인, 이메일 인증, 프로필 CRUD, 회원탈퇴/복구, 동의 API | DP-177, 178, 180, 181, 183, 184, 185, 187, 189, 196, 294 | ✅ 완료 |
| **A+. 포인트/배지** | 학습 행동별 포인트 적립, 배지 잠금 해제, 누적 포인트/스트릭/내역 조회 | DP-269 | ✅ 완료 (미커밋) |
| **B. 콘텐츠 피드** | 태그 기반 개인화 피드, 글 상세, 스크랩/좋아요, 검색, 태그 기반 추천 | DP-204, 205, 207, 208, 210 | ✅ 완료 |
| **B+. 콘텐츠 수집** | Stack Overflow API + Velog GraphQL 크롤러, POST /internal/contents 수신 | DP-199~202, 289 | ✅ 완료 |
| **C. AI 요약** | 레벨별 AI 요약 (입문/주니어/미들/시니어), Redis+MongoDB 3단계 캐시, 재시도 | DP-221 | ✅ 완료 |
| **C+. AI 퀴즈** | 콘텐츠 기반 레벨별 퀴즈 생성, 시도 이력 저장, 통과 시 포인트+히스토리 | — | ✅ 완료 (미커밋) |
| **D. Q&A/커뮤니티** | 게시글 CRUD, 답변 CRUD+채택, 댓글 CRUD, AI 질문 개선, AI 1차 답변, 유사 질문 | DP-229, 230, 233, 235, 239, 240 | ✅ 완료 |
| **E. 학습 히스토리** | 5가지 행동 유형 자동 기록, actionTypes+날짜 필터 조회, 활동 통계 | DP-248, 249, 293 | ✅ 완료 |
| **F. 주간 리포트** | 활동 집계, AI 인사이트, 공유 링크 생성/조회, 리포트 목록 | DP-256, 258 | ✅ 완료 |
| **Extra. 트렌딩** | Stack Overflow 기반 최근 7일 트렌딩 키워드 TOP 20 | DP-292 | ✅ 완료 |

### 🔜 2단계 (Epic G~I) — 캡스톤 개발 예정

| Epic | 기능 | 상태 |
|------|------|------|
| **G. 구인구직 연동** | 사람인 API 공고 매칭, 이력서 자동 생성, 회사별 면접 Q&A, 하이브레인넷 | ❌ 미구현 |
| **H. 최신 기술 동향** | 5개 소스 크롤링 확장, FAISS 자동 재인덱싱, 트렌드 요약 배치 | ❌ 미구현 |
| **I. 연구노트/퀴즈** | Q&A 모듈 저장·재사용, 복습 퀴즈, 로드맵 추천 | ❌ 미구현 |

---

## 3. 티켓 진행 현황

### ✅ 완료 티켓 (도메인별)

#### 인증/사용자 (DP-177~294)
| 티켓 | 기능 | 담당 |
|------|------|------|
| DP-177 | 이메일 회원가입 (`POST /auth/signup`) | 하영 |
| DP-178 | 이메일 인증 코드 발송/검증 | 하영 |
| DP-180 | 이메일 로그인 (`POST /auth/login`) | 하영 |
| DP-181 | Access Token 재발급 + MissingRequestCookieException 핸들러 | 하영 |
| DP-183 | GitHub 소셜 로그인 + 이메일 비공개 fallback | 하영 |
| DP-184 | Google 소셜 로그인 | 하영 |
| DP-185 | 로그아웃 | 하영 |
| DP-187 | 프로필 조회/수정 (`GET/PUT /users/me`) | 홍근 |
| DP-189 | 회원탈퇴 (soft delete) + 탈퇴 계정 복구 | 홍근 |
| DP-196 | 회원/인증 단위 테스트 | — |
| DP-283 | Railway 리버스 프록시 설정, refreshToken 쿠키 SameSite=None | 홍근 |
| DP-290 | 이메일 발송 JavaMailSender → Brevo HTTP API 교체 | 홍근 |
| DP-294 | 회원가입 시 동의 API (`user_consents` 테이블) | — |

#### 콘텐츠 (DP-199~289)
| 티켓 | 기능 | 담당 |
|------|------|------|
| DP-199~202 | 콘텐츠 수집 파이프라인 (SO + Velog 크롤러) | 하영 |
| DP-201 | Velog 크롤러 Origin 헤더 누락 버그 수정 | 하영 |
| DP-204 | 개인화 피드 (`GET /contents`) | 홍근 |
| DP-205 | 콘텐츠 상세 (`GET /contents/{id}`) | 홍근 |
| DP-207 | 스크랩/취소 | 홍근 |
| DP-208 | 좋아요/취소 | 홍근 |
| DP-210 | 콘텐츠 검색 | 홍근 |
| DP-221 | AI 요약 (Redis→MongoDB→FastAPI 3단계 캐시, 재시도) | 홍근 |
| DP-289 | POST /internal/contents (AI 레포 콘텐츠 수신, NormalizedContentDto 스키마) | 홍근 |

#### 커뮤니티 (DP-229~240)
| 티켓 | 기능 | 담당 |
|------|------|------|
| DP-229 | 게시글 작성/목록/상세/수정/삭제 | 홍근 |
| DP-230 | AI 질문 개선 (`POST /posts/refine`) | 홍근 |
| DP-233 | AI 1차 답변 생성 (`POST /posts/{id}/ai-answer`) | 홍근/수헌 |
| DP-235 | 유사 질문 조회 (`GET /posts/{id}/similar`) | 수헌 |
| DP-239 | 답변 작성/수정/삭제/채택 (비관적 락 적용) | 홍근 |
| DP-240 | 댓글 작성/삭제 | 하영 |

#### 히스토리/리포트 (DP-248~258)
| 티켓 | 기능 | 담당 |
|------|------|------|
| DP-248 | 학습 히스토리 API (actionTypes 필터, 날짜 범위) | 하영 |
| DP-249 | 활동 통계 API | 하영 |
| DP-256 | 주간 리포트 목록/상세/현재 주 조회 | 홍근 |
| DP-258 | 리포트 공유 링크 생성/조회 | 홍근 |
| DP-293 | 히스토리 FETCH JOIN + 페이징 메모리 이슈 수정, UTC 날짜 통일 | 홍근 |

#### 포인트/배지 (DP-269)
| 티켓 | 기능 | 담당 |
|------|------|------|
| DP-269 | 포인트 적립/조회/내역, 배지 정의/획득, BadgeSeeder | 홍근 |

#### 품질/기타 (DP-292~296)
| 티켓 | 기능 |
|------|------|
| DP-292 | 트렌딩 키워드 API (`GET /trends/keywords`) |
| DP-296 | DELETE 응답 204 수정, 유사 질문 N+1 수정, 커뮤니티 상세 페이지 API, 커버리지 개선 |

### ⚠️ 미커밋 (구현 완료, commit 필요)

| 항목 | 파일 |
|------|------|
| AI 퀴즈 전체 | `AiQuizController`, `AiQuizService`, `AiQuizDocument`, `AiQuizRepository`, `QuizAttempt`, `QuizAttemptRepository`, `AiQuizResponse`, `AiQuizResult`, `QuizSubmitRequest`, `QuizSubmitResponse` |
| AI 퀴즈 테스트 | `AiQuizControllerTest`, `AiQuizServiceTest` |
| 포인트 컨트롤러 | `PointController` + 테스트 |
| PointAction 수정 | `AI_QUIZ_PASS(5)` 추가 |
| AiServerClient 수정 | `fetchQuiz()` 메서드 추가 |
| ErrorCode 수정 | `AI_QUIZ_NOT_FOUND` 추가 |
| docker-compose.yml | 인프라 전체 구성 |
| docs/ | 설계 문서 디렉토리 |

### ❌ 미구현

| 항목 | 티켓 | 비고 |
|------|------|------|
| AWS EC2 프로덕션 배포 | DP-273~278 | MVP 완료 후 진행 |
| v0.1.0 배포 기록 | DP-280 | Railway 배포 완료 후 |
| Epic G (구인구직) | — | 캡스톤 4~5월 |
| Epic H (기술 동향) | — | 캡스톤 5월 |
| Epic I (연구노트) | — | 캡스톤 5~6월 |
| Nginx 설정 | — | docker-compose 미포함 |
| DB 마이그레이션 도구 | — | 현재 Hibernate DDL auto |

---

## 4. MVP 구조 안정화 필요사항

| 항목 | 현황 | 조치 |
|------|------|------|
| **미커밋 코드** | AI 퀴즈 전체 + PointController 미커밋 | 즉시 커밋 필요 |
| **API 스펙 동기화** | `devpick-api-spec.json`에 `/users/me/points`, `/users/me/badges` 엔드포인트 미반영 | 스펙 업데이트 필요 |
| **Nginx** | 아키텍처 설계에는 있으나 docker-compose에 미포함 | EC2 배포 전 추가 |
| **DB 마이그레이션** | Hibernate DDL auto-create 사용 중 (프로덕션 위험) | Flyway 도입 고려 |
| **FastAPI AI 서버** | docker-compose에 미포함, `http://host.docker.internal:8000` 하드코딩 | 통합 docker-compose 구성 필요 |

---

## 5. 기획과 설계 — 요구사항 정의 방법론

### Jira (이슈 트래킹)
- **총 이슈**: 170건 — Epic 14 · Task 128 · Story 19 · Spike 7 · Bug 2
- **완료율**: 77% (약 131건 Done)
- **워크플로**: `To Do` → `In Progress` → `Blocked` → `Done`
- **브랜치 연동**: 커밋·PR 제목에 `DP-{번호}` 삽입 → Jira 자동 연동
- **Epic 구조**: A(회원) · B(피드) · C(AI요약) · D(커뮤니티) · E(히스토리) · F(리포트) · G(구인) · H(동향) · I(연구노트) + 인프라 5개 Epic

### Confluence (지식 관리)
- **총 페이지**: 64건
  - ADR (Architecture Decision Records): 12건
  - 회의록: 12건 (데일리 10분 · 위클리 플래닝 45분 · 데모/리뷰 30분 · 회고 30분)
  - 트러블슈팅: 6건 (TRB-001~005 + 추가)
  - API 명세: 4건
  - DB 설계, PRD v1.0/v1.1, AI Golden Set 4종, 온보딩 가이드 포함

### 주요 ADR 결정 요약

| ADR | 결정 내용 |
|-----|----------|
| ADR-001 | PostgreSQL (구조화) + MongoDB (AI 결과물/이벤트) 분리 |
| ADR-002 | JWT Access Token + Refresh Token (HttpOnly Cookie) |
| ADR-003 | API 에러 포맷: `{success, error:{code, message, detail}}` |
| ADR-007 | Redis 캐시: AI 요약/퀴즈 7일, 피드 10분, 리포트 7일 |

### 스프린트 운영
- **주기**: 1~2주 단위
- **정기 미팅**: 매주 월요일 13~14시
- **회고**: KPT (Keep/Problem/Try) → Try 항목을 다음 스프린트 Jira 티켓으로 전환

---

## 6. 테이블 정의서

### PostgreSQL (25개 테이블)

#### 인증/사용자 (6개)

| 테이블 | 핵심 컬럼 |
|--------|-----------|
| `users` | `id(UUID, PK)`, `email(UNIQUE)`, `password_hash`, `nickname(UNIQUE)`, `profile_image`, `job(enum)`, `level(enum)`, `total_points(INT default 0)`, `is_active`, `is_email_verified`, `deleted_at` |
| `social_accounts` | `id(UUID)`, `user_id(FK→users)`, `provider(github/google)`, `provider_id`, UNIQUE(provider, provider_id) |
| `refresh_tokens` | `id(UUID)`, `user_id(FK)`, `token`, `expires_at` |
| `email_verifications` | `id(UUID)`, `email`, `code`, `is_verified`, `expires_at` |
| `tags` | `id(UUID)`, `name(UNIQUE)` |
| `user_tags` | `user_id(FK)`, `tag_id(FK)`, UNIQUE(user_id, tag_id) |

#### 콘텐츠 (5개)

| 테이블 | 핵심 컬럼 |
|--------|-----------|
| `content_sources` | `id(UUID)`, `name`, `url`, `collect_method(api/rss/graphql)`, `is_active` |
| `contents` | `id(UUID)`, `source_id(FK)`, `title`, `author`, `canonical_url(UNIQUE)`, `preview`, `original_content(TEXT)`, `thumbnail_url`, `license_type`, `is_original_visible`, `is_available`, `score`, `view_count`, `published_at` |
| `content_tags` | `content_id(FK)`, `tag_id(FK)`, UNIQUE(content_id, tag_id) |
| `scraps` | `id(UUID)`, `user_id(FK)`, `content_id(FK)`, `created_at`, UNIQUE(user_id, content_id) |
| `likes` | `id(UUID)`, `user_id(FK)`, `content_id(FK)`, `created_at`, UNIQUE(user_id, content_id) |

#### 커뮤니티 (8개)

| 테이블 | 핵심 컬럼 |
|--------|-----------|
| `posts` | `id(UUID)`, `user_id(FK)`, `title`, `content(TEXT, 마크다운)`, `level(enum)`, `created_at`, `updated_at` |
| `answers` | `id(UUID)`, `post_id(FK)`, `user_id(FK)`, `content(TEXT)`, `is_adopted(boolean)` |
| `comments` | `id(UUID)`, `answer_id(FK)`, `user_id(FK)`, `content` |
| `ai_questions` | `id(UUID)`, `post_id(FK)`, `original_title`, `refined_title`, `suggestions(JSONB)` |
| `ai_answers` | `id(UUID)`, `post_id(FK)`, `content(TEXT)`, `is_adopted(boolean)` |
| `similar_questions` | `id(UUID)`, `post_id(FK)`, `similar_id(FK→posts)`, `score(FLOAT)` |
| `post_likes` | `id(UUID)`, `post_id(FK)`, `user_id(FK)`, UNIQUE(post_id, user_id) |
| `answer_likes` | `id(UUID)`, `answer_id(FK)`, `user_id(FK)`, UNIQUE(answer_id, user_id) |

#### 히스토리/리포트 (3개)

| 테이블 | 핵심 컬럼 |
|--------|-----------|
| `history` | `id(UUID)`, `user_id(FK)`, `action_type(VARCHAR)`, `content_id(UUID, nullable)`, `post_id(UUID, nullable)`, `created_at` |
| `weekly_reports` | `id(UUID)`, `user_id(FK)`, `week_start(DATE)`, `share_token(UNIQUE)`, `status`, UNIQUE(user_id, week_start) |
| `report_activities` | `id(UUID)`, `report_id(FK)`, `contents_read(INT)`, `questions_created(INT)`, `top_tags(JSONB)` |

> **history.action_type 허용값**: `content_opened`, `ai_summary_viewed`, `scrapped`, `question_created`, `post_created`, `ai_quiz_completed`

#### 포인트/배지 (3개)

| 테이블 | 핵심 컬럼 |
|--------|-----------|
| `point_logs` | `id(UUID)`, `user_id(FK)`, `action(PointAction enum)`, `points(INT)`, `reference_id(UUID, nullable)`, `earned_at` |
| `badges` | `id(VARCHAR 50, PK)`, `name(VARCHAR 100)`, `description`, `sort_order(INT)`, `created_at` |
| `user_badges` | `id(UUID)`, `user_id(FK)`, `badge_id(FK)`, `acquired_at`, UNIQUE(user_id, badge_id) |

#### AI 퀴즈 (1개)

| 테이블 | 핵심 컬럼 |
|--------|-----------|
| `quiz_attempts` | `id(UUID)`, `user_id(FK)`, `content_id(FK)`, `level(VARCHAR 20)`, `score(INT)`, `total_questions(INT)`, `passed(BOOLEAN)`, `created_at` |

#### 기타 (1개)

| 테이블 | 핵심 컬럼 |
|--------|-----------|
| `user_consents` | `id(UUID)`, `user_id(FK)`, `consent_type`, `agreed_at`, UNIQUE(user_id, consent_type) |

---

### MongoDB (4개 컬렉션)

| 컬렉션 | 인덱스 | 주요 필드 |
|--------|--------|-----------|
| `ai_summaries` | `content_id + level` (UNIQUE) | `content_id`, `level`, `core_summary`, `key_points[]`, `keywords[]`, `difficulty`, `next_recommendation`, `confidence`, `additional_questions[]`, `cached_at`, `expires_at` |
| `ai_quizzes` | `content_id + level` (UNIQUE) | `content_id`, `level`, `title`, `questions[]{id, question, options[], correct_option_id, explanation}`, `passing_count`, `estimated_minutes`, `cached_at`, `expires_at` |
| `weekly_report_insights` | `report_id` (UNIQUE) | `report_id`, `user_id`, `well_done`, `lacking`, `next_week`, `generated_at` |
| `event_logs` | `user_id`, `event_type`, `created_at` | `user_id`, `event_type`, `properties(JSONB)`, `created_at` |

---

### Redis (캐시 키 패턴)

| 키 패턴 | TTL | 용도 |
|---------|-----|------|
| `summary:{contentId}:{level}` | 7일 | AI 요약 캐시 |
| `quiz:{contentId}:{level}` | 7일 | AI 퀴즈 캐시 |
| `feed:{userId}:page:{page}` | 10분 | 개인화 피드 캐시 |
| `report:{userId}:{weekStart}` | 7일 | 주간 리포트 캐시 |
| `email:verify:{email}` | 5분 | 이메일 인증 코드 |
| `email:verify:attempts:{email}` | 5분 | 인증 시도 횟수 |
| `email:verify:cooldown:{email}` | 1분 | 재전송 쿨다운 |

---

## 7. API 명세서

**공통 응답 포맷:**
```json
// 성공
{ "success": true, "data": { ... } }

// 에러
{ "success": false, "error": { "code": "AUTH_001", "message": "...", "detail": "..." } }
```

**인증**: `Authorization: Bearer {access_token}` (O = 필요, X = 불필요)

---

### 인증 (Auth)

| Method | Path | 인증 | 응답 코드 |
|--------|------|------|-----------|
| POST | `/auth/signup` | X | 201, 400, 409 |
| POST | `/auth/login` | X | 200, 401, 409 |
| POST | `/auth/logout` | O | 200, 401 |
| POST | `/auth/refresh` | X | 200, 401 |
| POST | `/auth/recover` | X | 200, 404, 410 |
| POST | `/auth/social/recover` | X | 200, 410 |
| POST | `/auth/email/send` | X | 200, 429 |
| POST | `/auth/email/verify` | X | 200, 400 |
| GET | `/auth/github` | X | 302 |
| GET | `/auth/github/callback` | X | 200 |
| GET | `/auth/google` | X | 302 |
| GET | `/auth/google/callback` | X | 200 |

### 사용자 (Users)

| Method | Path | 인증 | 응답 코드 |
|--------|------|------|-----------|
| GET | `/users/me` | O | 200, 401 |
| PUT | `/users/me` | O | 200, 400, 401, 409 |
| DELETE | `/users/me` | O | 204, 401 |
| GET | `/users/me/points` | O | 200, 401 |
| GET | `/users/me/points/history` | O | 200, 401 |
| GET | `/users/me/badges` | O | 200, 401 |

### 콘텐츠 (Contents)

| Method | Path | 인증 | 응답 코드 |
|--------|------|------|-----------|
| GET | `/contents` | O | 200, 401 |
| GET | `/contents/search` | O | 200, 401 |
| GET | `/contents/{id}` | O | 200, 401, 404 |
| GET | `/contents/{id}/recommendations` | O | 200, 401 |
| POST | `/contents/{id}/scrap` | O | 201, 401, 409 |
| DELETE | `/contents/{id}/scrap` | O | 204, 401, 404 |
| POST | `/contents/{id}/like` | O | 201, 401, 409 |
| DELETE | `/contents/{id}/like` | O | 204, 401, 404 |
| GET | `/contents/{id}/summary` | O | 200, 401, 503 |
| POST | `/contents/{id}/summary/retry` | O | 200, 401, 503 |
| GET | `/contents/{id}/quiz` | O | 200, 401, 404, 503 |
| POST | `/contents/{id}/quiz/submit` | O | 200, 401, 404 |

### 커뮤니티 (Community)

| Method | Path | 인증 | 응답 코드 |
|--------|------|------|-----------|
| GET | `/posts` | O | 200 |
| POST | `/posts` | O | 201, 401 |
| POST | `/posts/refine` | O | 200, 503 |
| GET | `/posts/{postId}` | O | 200, 404 |
| PUT | `/posts/{postId}` | O | 200, 401, 403, 404 |
| DELETE | `/posts/{postId}` | O | 204, 401, 403, 404 |
| GET | `/posts/{postId}/similar` | O | 200 |
| POST | `/posts/{postId}/ai-answer` | O | 200, 503 |
| POST | `/posts/{postId}/answers` | O | 201, 401, 404 |
| PUT | `/posts/{postId}/answers/{aid}` | O | 200, 401, 403 |
| DELETE | `/posts/{postId}/answers/{aid}` | O | 204, 401, 403 |
| POST | `/posts/{postId}/answers/{aid}/adopt` | O | 200, 401, 403, 409 |
| POST | `/posts/{postId}/answers/{aid}/comments` | O | 201, 401 |
| DELETE | `/posts/{postId}/answers/{aid}/comments/{cid}` | O | 204, 401, 403 |

### 히스토리 (History)

| Method | Path | 인증 | 응답 코드 |
|--------|------|------|-----------|
| GET | `/history` | O | 200, 401 |
| GET | `/history/activity` | O | 200, 401 |

### 리포트 (Reports)

| Method | Path | 인증 | 응답 코드 |
|--------|------|------|-----------|
| GET | `/reports/weekly/list` | O | 200, 401 |
| GET | `/reports/weekly` | O | 200, 401 |
| GET | `/reports/weekly/{reportId}` | O | 200, 401, 404 |
| POST | `/reports/weekly/{reportId}/share` | O | 201, 401, 404 |
| GET | `/reports/weekly/share/{token}` | X | 200, 404 |

### 트렌딩 (Trends)

| Method | Path | 인증 | 응답 코드 |
|--------|------|------|-----------|
| GET | `/trends/keywords` | O | 200 |

### 내부 API (Internal — Nginx에서 외부 차단)

| Method | Path | 인증 | 응답 코드 |
|--------|------|------|-----------|
| POST | `/internal/contents` | X (내부망) | 200 |

### 기타

| Method | Path | 인증 | 응답 코드 |
|--------|------|------|-----------|
| GET | `/health` | X | 200 |

---

### 에러 코드 네임스페이스

| 접두사 | 도메인 |
|--------|--------|
| `AUTH_` | 인증 |
| `USER_` | 사용자 |
| `CONTENT_` | 콘텐츠 |
| `AI_` | AI 기능 |
| `COMMUNITY_` | 커뮤니티 |
| `POINT_` | 포인트 |
| `BADGE_` | 배지 |

---

## 8. CI/CD 파이프라인

### GitHub Actions 워크플로

| 파일 | 트리거 | 역할 |
|------|--------|------|
| `ci.yml` | PR → develop, PR → main | 빌드 · 테스트 · SonarCloud 분석 |
| `auto-merge.yml` | PR → develop (`auto/` 브랜치만) | CI 통과 시 자동 squash 머지 |

### ci.yml 구조

```
Job 1: build-test
  Services: postgres:16 + redis:7 + mongodb:7
  ./gradlew build --no-daemon (SPRING_PROFILES_ACTIVE=test)
       ↓ 성공해야만
Job 2: sonar (needs: build-test)
  ./gradlew sonar --no-daemon -Dsonar.qualitygate.wait=true
  Quality Gate: 커버리지 ≥80%, 중복 ≤3%, Security 0개
       ↓ 모두 green
auto-merge.yml (auto/ 브랜치만)
  gh pr merge --auto --squash → develop Squash Merge
```

### SonarCloud Quality Gate 기준

| 항목 | 기준 |
|------|------|
| 신규 코드 커버리지 | ≥ 80% |
| 코드 중복률 | ≤ 3% |
| Security Hotspot | 0개 |
| 버그/취약점 | 0개 |

### 브랜치 전략

| 브랜치 | 생성 주체 | 머지 방식 |
|--------|-----------|-----------|
| `auto/feature/DP-{번호}` | Claude Code (신규 기능) | CI 통과 시 자동 squash merge |
| `auto/fix/DP-{번호}` | Claude Code (버그 수정) | CI 통과 시 자동 squash merge |
| `feature/DP-{번호}` | 개발자 직접 | 팀원 리뷰 후 수동 merge |
| `hotfix/DP-{번호}` | 개발자 직접 | 팀원 리뷰 후 수동 merge |

---

## 9. 앞으로 할 일

### 즉시 (커밋 필요)
- [ ] AI 퀴즈 전체 코드 커밋 (`AiQuizController` ~ `QuizAttemptRepository`)
- [ ] `PointController` 커밋
- [ ] `PointAction.AI_QUIZ_PASS`, `AiServerClient.fetchQuiz()`, `ErrorCode.AI_QUIZ_NOT_FOUND` 커밋
- [ ] `devpick-api-spec.json`에 포인트/배지 엔드포인트 추가

### MVP 안정화 (2026-04-13 데드라인)
- [ ] AWS EC2 프로덕션 배포 (DP-273~278)
- [ ] Nginx SSL + 도메인 라우팅 설정
- [ ] docker-compose에 FastAPI AI 서버 통합
- [ ] v0.1.0 배포 기록 (DP-280)

### 캡스톤 (2026-04 ~ 2026-06)

| 기간 | Epic | 핵심 작업 |
|------|------|-----------|
| 04월 2~4주 | G. 구인구직 | 사람인 API 연동, 히스토리 분석, 이력서 자동 생성, 면접 Q&A |
| 05월 1~2주 | H. 기술 동향 | 크롤링 소스 확장, FAISS 자동 재인덱싱, 트렌드 배치 |
| 05월 3~4주 | I. 연구노트 | Q&A 모듈화 저장, 복습 퀴즈, 로드맵 추천 |
| 06월 1주 | 통합 테스트 | E2E 시나리오 테스트, 버그 수정, 발표 준비 |
| **06-04** | **발표전시회** | 3개 시나리오 데모 |

---

## 10. 트러블슈팅 이력

> 전체 기록: `TRB.md`

| ID | 날짜 | 내용 | 해결 |
|----|------|------|------|
| TRB-001 | 2026-03-07 | Squash Merge + Cascade 브랜치 머지 충돌 | GitHub Git Data API로 서버사이드 해결 |
| TRB-002 | 2026-03-07 | git commit 서명 서버 오류 (`source: Field required`) | 새 대화 시작으로 재초기화 |
| TRB-003 | 2026-03-08 | springdoc 2.8.6 + Spring Boot 4.x 호환 불가 | `@Profile("!test")` + yml 비활성화 |
| TRB-004 | 2026-03-09 | SonarCloud QG 실패해도 auto-merge 실행 | ci.yml sonar job exit 1 + required check 등록 |
| TRB-005 | 2026-03-10 | SonarCloud `STATUS=NONE` 타임아웃 오탐 | `-Dsonar.qualitygate.wait=true` 옵션 |
