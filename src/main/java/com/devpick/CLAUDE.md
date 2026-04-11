# Trace Backend — 도메인 & DB 구조

> 이 파일은 `com.devpick` 패키지 하위 코드 작업 시 참고한다.
> 상위 컨텍스트: 루트 `CLAUDE.md`

---

## 1. 도메인별 책임

| 도메인 | 패키지 | 핵심 책임 | 구현 상태 |
|--------|--------|-----------|-----------|
| user | `domain.user` | 회원가입/로그인/소셜인증/프로필/토큰 관리 | ✅ 완료 |
| content | `domain.content` | 콘텐츠 피드/스크랩/좋아요/검색/AI요약/AI퀴즈/수집 파이프라인 | ✅ 완료 |
| community | `domain.community` | 게시글/답변/AI질문개선/AI답변/댓글 | ✅ 완료 |
| report | `domain.report` | 주간 리포트 생성/조회/공유 + 학습 히스토리 (※ 설계상 `domain.history` 분리 예정이나 현재 report 하위에 있음) | ✅ 완료 |
| point | `domain.point` | 포인트 적립/조회 + 배지 잠금해제/조회 (DP-269) | ✅ 완료 |

---

## 2. PostgreSQL 테이블 목록 (24개)

### 인증/사용자
| 테이블 | 설명 | 핵심 컬럼 |
|--------|------|-----------|
| `users` | 사용자 | `id(UUID)`, `email(UNIQUE)`, `password_hash`, `nickname(UNIQUE)`, `profile_image`, `job`, `level`, `is_active`, `is_email_verified`, `deleted_at`(soft delete), `total_points`(INT, default 0) |
| `social_accounts` | 소셜 로그인 | `user_id(FK)`, `provider(github/google)`, `provider_id` |
| `refresh_tokens` | JWT Refresh Token | `user_id(FK)`, `token`, `expires_at` |
| `email_verifications` | 이메일 인증 발송 이력 | `email`, `is_verified` |
| `tags` | 기술 태그 | `name(UNIQUE)` |
| `user_tags` | 사용자-태그 N:M | `user_id(FK)`, `tag_id(FK)` |

### 콘텐츠
| 테이블 | 설명 | 핵심 컬럼 |
|--------|------|-----------|
| `content_sources` | 수집 소스 | `name`, `url`, `collect_method(api/rss/graphql)`, `is_active` |
| `contents` | 수집된 글 | `source_id(FK)`, `canonical_url(UNIQUE)`, `is_original_visible`, `is_available`, `license_type` |
| `content_tags` | 콘텐츠-태그 N:M | `content_id(FK)`, `tag_id(FK)` |
| `scraps` | 스크랩 | `user_id+content_id(UNIQUE 복합)` |
| `likes` | 좋아요 | `user_id+content_id(UNIQUE 복합)` — 학습 히스토리 미포함 |

### 커뮤니티
| 테이블 | 설명 | 핵심 컬럼 |
|--------|------|-----------|
| `posts` | 게시글 | `user_id(FK)`, `title`, `content(마크다운)`, `level` |
| `ai_questions` | AI 질문 개선 기록 | `post_id(FK)`, `original_title`, `refined_title`, `suggestions(JSONB)` |
| `ai_answers` | AI 답변 | `post_id(FK)`, `content`, `is_adopted` |
| `similar_questions` | 유사 질문 | `post_id(FK)`, `similar_id(FK)`, `score(FLOAT)` |
| `post_likes` | 게시글 좋아요 | `post_id+user_id(UNIQUE 복합)` |
| `answers` | 사용자 답변 | `post_id(FK)`, `user_id(FK)`, `is_adopted` |
| `answer_likes` | 답변 좋아요 | `answer_id+user_id(UNIQUE 복합)` |
| `comments` | 댓글 (1 depth만) | `answer_id(FK)`, `user_id(FK)` |

### 히스토리/리포트
| 테이블 | 설명 | 핵심 컬럼 |
|--------|------|-----------|
| `history` | 학습 행동 기록 | `user_id(FK)`, `action_type`, `content_id(NULL)`, `post_id(NULL)` |
| `weekly_reports` | 주간 리포트 | `user_id+week_start(UNIQUE 복합)`, `share_token`, `status` |
| `report_activities` | 리포트 활동 집계 | `report_id(FK)`, `contents_read`, `questions_created`, `top_tags(JSONB)` |

### 포인트/배지
| 테이블 | 설명 | 핵심 컬럼 |
|--------|------|-----------|
| `point_logs` | 포인트 적립 기록 | `id(UUID)`, `user_id(FK)`, `action(VARCHAR 50)`, `points(INT)`, `reference_id(UUID, nullable)`, `earned_at` |
| `badges` | 배지 정의 | `id(VARCHAR 50, PK)`, `name(VARCHAR 100)`, `description(VARCHAR 255)`, `sort_order(INT)`, `created_at` |
| `user_badges` | 사용자 획득 배지 | `id(UUID)`, `user_id(FK)`, `badge_id(FK)`, `acquired_at` — UNIQUE(user_id, badge_id) |

### AI 퀴즈
| 테이블 | 설명 | 핵심 컬럼 |
|--------|------|-----------|
| `quiz_attempts` | 퀴즈 시도 이력 | `user_id(FK)`, `content_id(FK)`, `level(VARCHAR 20)`, `score(INT)`, `total_questions(INT)`, `passed(BOOLEAN)` — INDEX(user_id, content_id) |

### history.action_type 허용값
```
content_opened       — 글 상세 진입 (학습 기록 O)
ai_summary_viewed    — AI 요약 조회 (학습 기록 O)
scrapped             — 스크랩 (학습 기록 O)
question_created     — 질문 작성 (학습 기록 O)
post_created         — 커뮤니티 게시 (학습 기록 O)
ai_quiz_completed    — AI 퀴즈 통과 (학습 기록 O)
```
> **주의**: `content_liked`는 학습 기록 X (activity에만 표시)

---

## 3. DynamoDB 테이블 (AI 문서·RAG)

> PK/SK·TTL·속성 전체: **[docs/table-definition.md](../../../../docs/table-definition.md)** §4. 코드: `AiSummaryDocument`, `AiQuizDocument`, `WeeklyReportInsightDocument` 등.

### ai_summaries

| 항목 | 설명 |
|------|------|
| 키 | `content_id`(Hash) + `level`(Range) |
| difficulty | AI와 동일: `easy` \| `medium` \| `hard` (OpenAPI·저장값 일치) |
| 기타 | `core_summary`, `key_points`, `keywords`, `next_recommendation`, `confidence`, `additional_questions`, `cached_at`, `expires_at`(TTL) |

### ai_quizzes

| 항목 | 설명 |
|------|------|
| 키 | `content_id`(Hash) + `level`(Range) |
| `title` | 저장 시 콘텐츠 제목(글 제목) 사용 |
| `questions[]` | `id`, `type`, `question`, `options`, `correct_option_id`, `explanation`, **`correct_answer`**(주관식 단답; 객관식은 `""`) |
| 기타 | `passing_count`, `estimated_minutes`, `cached_at`, `expires_at`(TTL) |

### weekly_report_insights

| 항목 | 설명 |
|------|------|
| 키 | `report_id`(Hash) |
| 속성 | `user_id`, `well_done`, `lacking`, `next_week`, `generated_at` |

### rag_documents / rag_questions

RAG 청크·임베딩 메타데이터. 상세는 `docs/table-definition.md` §4.4~4.5.

---

## 4. Redis 캐시 전략 (ADR-007)

| 데이터 | 캐시 키 | TTL |
|--------|---------|-----|
| AI 요약 결과 | `summary:{contentId}:{level}` | 7일 |
| AI 퀴즈 결과 | `quiz:{contentId}:{level}` | 7일 |
| 개인화 피드 | `feed:{userId}:page:{page}` | 10분 |
| 주간 리포트 | `report:{userId}:{weekStart}` | 7일 |
| 이메일 인증 코드 | `email:verify:{email}` | 5분 |
| 이메일 인증 시도 횟수 | `email:verify:attempts:{email}` | 5분 |
| 이메일 재전송 쿨다운 | `email:verify:cooldown:{email}` | 1분 |

**캐시 미스 처리 흐름:**
```
캐시 미스 → DynamoDB 조회 (이전 결과 있으면 재사용)
         → 없으면 FastAPI 호출
         → Redis + DynamoDB 저장
```

**캐시 무효화:** 콘텐츠 업데이트 시 4개 레벨(입문/주니어/미들/시니어) 캐시 삭제

---

## 5. Spring → FastAPI 통신

Spring Boot가 FastAPI로 내부 REST 통신으로 요청:

| 기능 | Spring 엔드포인트 | FastAPI 호출 |
|------|-----------------|-------------|
| AI 요약 조회 | `GET /contents/{id}/summary` | FastAPI 요약 API |
| AI 질문 개선 | `POST /posts/refine` | FastAPI 질문 개선 API |
| AI 1차 답변 | `POST /posts/{id}/ai-answer` | FastAPI 답변 API |
| AI 인사이트 | (배치) | FastAPI 리포트 API |

FastAPI 내부 URL: `http://localhost:8000` (Docker 환경: `http://ai-server:8000`)

---

## 6. 콘텐츠 수집 파이프라인

```
Stack Overflow API / Velog GraphQL / RSS
        ↓ (Spring Batch, 하루 1~2회)
    원본 수집 → 정규화 → JPA UPSERT(중복제거)
        ↓
    PostgreSQL (contents 테이블)
        ↓
    FastAPI로 전달 → FAISS 벡터 인덱싱
```

담당: **하영** (DP-199~202)

---

## 7. 답변 채택 주의사항

답변 채택 API (`POST /posts/{postId}/answers/{answerId}/adopt`)는
`@Transactional + 비관적 락` 적용 필요 (DP-239 참고).

이유: 동시에 여러 답변이 채택되는 race condition 방지.

---

## 8. 이벤트 타입 전체 목록

PRD 9번 기반 — 학습 행동은 PostgreSQL `history` 등으로 기록 (별도 MongoDB `event_logs` 컬렉션 없음)

| 이벤트 | properties |
|--------|------------|
| `user_signed_up` | `{provider: "github/google/email"}` |
| `profile_updated` | `{fields: ["level", "tags"]}` |
| `content_opened` | `{content_id, tags[]}` |
| `ai_summary_viewed` | `{content_id, level}` |
| `content_saved` | `{content_id}` |
| `content_liked` | `{content_id}` |
| `question_created` | `{post_id}` |
| `question_refined` | `{post_id}` |
| `ai_answer_generated` | `{post_id}` |
| `comment_created` | `{post_id, answer_id}` |
| `answer_adopted` | `{post_id, answer_id}` |
| `weekly_report_generated` | `{report_id}` |
| `weekly_report_viewed` | `{report_id}` |
| `ai_quiz_completed` | `{content_id, level, score, passed: true}` |
