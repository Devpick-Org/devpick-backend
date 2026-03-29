# Trace — 다이어그램 설계 명세

> 제안서 그림 1~5 작성 시 참고. 코드 기반으로 확인된 실제 플로우만 기재.

---

## 그림 1 — 시스템 아키텍처 (정적 구조)

```
[브라우저]
    │
    │ HTTP/HTTPS
    ▼
[Nginx :80/443]
  ├─ devpick.kr      ──────────► [Next.js :3000]
  └─ api.devpick.kr  ──────────► [Spring Boot :8080]
                                      │
                          JwtAuthenticationFilter
                          (모든 요청 JWT 검증)
                                      │
                    ┌─────────────────┼─────────────────┐
                    ▼                 ▼                 ▼
           [PostgreSQL :5432]  [MongoDB :27017]  [Redis :6379]
           구조화 도메인 데이터   AI 결과물 캐시    고속 캐시/세션
           ─────────────────   ───────────────   ─────────────
           users               ai_summaries      summary:{id}:{level}
           contents            ai_quizzes        quiz:{id}:{level}
           posts / answers     weekly_report     JWT refresh token
           history             _insights         OAuth state
           point_logs          event_logs        email verify code
           badges / user_badges
           weekly_reports
           quiz_attempts
                                      │
                                      │ WebClient (내부 REST)
                                      ▼
                               [FastAPI :8000]
                               ├─ POST /api/summary  (AI 요약 생성)
                               ├─ POST /api/quiz     (AI 퀴즈 생성)
                               ├─ POST /api/refine   (질문 개선)
                               ├─ POST /api/answer   (AI 답변)
                               ├─ FAISS 벡터 인덱스
                               └─ Claude API (LLM)
```

**포트 정리**

| 서버 | 포트 | 비고 |
|------|------|------|
| Nginx | 80 / 443 | SSL 종단, 도메인 라우팅 |
| Next.js | 3000 | 프론트엔드 |
| Spring Boot | 8080 | REST API 서버 |
| FastAPI | 8000 | AI 서버 (`${ai.server.url}`) |
| PostgreSQL | 5432 | 메인 DB |
| MongoDB | 27017 | AI 결과물 캐시 DB |
| Redis | 6379 | 캐시 / 세션 |

---

## 그림 2 — 콘텐츠 수집 파이프라인

```
[FastAPI 크롤러]
  수집 소스 (병렬):
  ├─ Stack Overflow API     (1시간 주기)
  ├─ Velog GraphQL          (2시간 주기)
  ├─ GitHub Trending RSS    (6시간 주기)
  ├─ Hacker News API        (1시간 주기)
  └─ Tech 블로그 RSS        (6시간 주기)
         │
         │ 정규화: NormalizedContentDto
         │  { sourceName, title, author, canonicalUrl,
         │    publishedAt, preview, bodyCandidate,
         │    isOriginalVisible, thumbnailUrl, tags[] }
         │
         ▼
POST /internal/contents
         │
         ▼
[InternalContentService.ingest()]  @Transactional
         │
         │ for (each item):
         │
         ├─► ContentSourceRepository
         │     .findByNameAndIsActiveTrue(sourceName)
         │         소스 없음 → CONTENT_SOURCE_NOT_FOUND 예외
         │
         ├─► ContentRepository.save(Content)
         │     성공           → saved++
         │     DataIntegrityViolationException
         │     (canonical_url UNIQUE 위반)
         │                   → skipped++ (중복, 정상 스킵)
         │
         ▼
[PostgreSQL contents 테이블]
  UPSERT 결과: { saved: N, skipped: M }
         │
         ▼ (FastAPI 자체 처리)
[FAISS 벡터 인덱스]
  신규 문서 임베딩 → 인덱스 실시간 추가
```

---

## 그림 3 — AI 요약 / 퀴즈 플로우 (3단계 캐시)

### AI 요약: GET /contents/{contentId}/summary?level=JUNIOR

```
클라이언트
    │ GET /contents/{contentId}/summary?level=JUNIOR
    ▼
AiSummaryController.getSummary()
    │
    ▼
① ContentRepository.findByIdAndIsAvailableTrue(contentId)
      없음 → CONTENT_NOT_FOUND

② Redis.get("summary:{contentId}:JUNIOR")
      ┌─ HIT ──────────────────────────────────────┐
      │  HistoryRepository.save(ai_summary_viewed) │
      │  PointService.earn(AI_SUMMARY_VIEW)        │
      │  → 응답 반환                               │
      └────────────────────────────────────────────┘
      │ MISS
      ▼
③ AiSummaryRepository                 [MongoDB]
     .findByContentIdAndLevel()
      ┌─ 있고 expiresAt > now ──────────────────────┐
      │  Redis.set("summary:...", TTL 7일)          │
      │  → 응답 반환                                │
      └─────────────────────────────────────────────┘
      │ 없거나 만료
      ▼
④ AiServerClient.fetchSummary()
     WebClient POST http://ai-server:8000/api/summary
     Body: { content_id, level }
     ← AiSummaryResult {
         coreSummary, keyPoints, keywords,
         difficulty, nextRecommendation,
         confidence, additionalQuestions
       }

⑤ AiSummaryRepository.save(doc)       [MongoDB, expiresAt = now+7일]
   Redis.set("summary:{id}:{level}")   [TTL 7일]
   HistoryRepository.save(ai_summary_viewed)
   PointService.earn(AI_SUMMARY_VIEW)
    │
    ▼
응답 반환: AiSummaryResponse
```

### AI 퀴즈 조회: GET /contents/{contentId}/quiz?level=JUNIOR

```
① ContentRepository.findByIdAndIsAvailableTrue(contentId)

② QuizAttemptRepository
     .findTopByUser_IdAndContent_IdOrderByCreatedAtDesc()
   (이전 시도 이력 조회 — 유저별 점수/통과 여부 표시용)

③ Redis.get("quiz:{contentId}:JUNIOR")
      HIT → lastAttempt와 병합 → 응답

④ AiQuizRepository.findByContentIdAndLevel()  [MongoDB]
      있고 유효 → Redis 재저장 → 응답

⑤ AiServerClient.fetchQuiz()
     WebClient POST http://ai-server:8000/api/quiz
     Body: { content_id, level }
     ← AiQuizResult {
         questions[{ id, question, options[], correctOptionId, explanation }],
         passingCount, estimatedMinutes
       }

⑥ AiQuizRepository.save(doc)          [MongoDB, expiresAt = now+7일]
   Redis.set("quiz:{id}:{level}")      [TTL 7일]
```

### 퀴즈 제출: POST /contents/{contentId}/quiz/submit

```
QuizSubmitRequest { level, score, totalQuestions, passed }
    │
    ▼
QuizAttemptRepository.save(QuizAttempt)   [PostgreSQL quiz_attempts 테이블]
  { user_id, content_id, level, score, totalQuestions, passed }
    │
    │ passed == true?
    ├─ YES
    │    HistoryRepository.save(History { actionType: "ai_quiz_completed" })
    │    PointService.earn(user, AI_QUIZ_PASS, referenceId=contentId)
    │      │
    │      │ isDuplicate() 중복 체크:
    │      │  PointLogRepository
    │      │    .existsByUser_IdAndActionAndReferenceId(userId, AI_QUIZ_PASS, contentId)
    │      │  → 동일 contentId로 이미 통과 이력 있으면 포인트 지급 안 함
    │      │
    │      ▼
    │    PointLogRepository.save(PointLog { points: 5 })
    │    user.addPoints(5) → UserRepository.save()
    │    BadgeService.checkAndUnlock(user)
    │
    └─ NO  (그냥 시도 기록만)
    │
    ▼
QuizSubmitResponse { passed, score, totalQuestions, pointsEarned }
```

---

## 그림 4 — 구인구직 연계 파이프라인 (Epic G — 설계)

> **미구현 (캡스톤 개발 예정)**. 아래는 설계 기반 플로우.

```
[PostgreSQL history 테이블]
  user_id 기준 최근 90일 조회
  태그별 학습 횟수 · 최근 활동량 집계
    │
    ▼
기술 스택 수치화
  { "Spring Boot": 22회, "Java": 15회, "Docker": 8회, "Redis": 5회 }
    │
    ▼
Claude API — 이력서 생성
  Input:  기술 스택 JSON + 직무 + 레벨
  Output: JSON Schema 기반 구조화 이력서 초안
  (사용자 직접 수정 가능)
    │
    ▼
사람인 Open API
  보유 기술 태그 → 검색 파라미터 자동 변환
  1회 최대 500개 공고 조회
  기술 일치율 스코어링 → 상위 20개 추천
  Redis 캐시 TTL 1시간 (일일 요청 횟수 제한 대응)
    │
    │ 사용자가 목표 공고 선택
    ▼
공고 요구 기술 파싱
  { 요구: [Kotlin, Spring, Kafka, MySQL, Kubernetes]
    보유: [Java, Spring Boot, Docker, Redis]
    부족: [Kotlin, Kafka] }
    │
    ▼
Claude API — 면접 Q&A 생성
  회사 맞춤 면접 예상 질문 10개 + 모범 답변 + 부족 역량 분석
    │
    ▼
부족 역량 보완 추천
  ├─ Trace 최신 콘텐츠 상위 5개  (FAISS 유사도 검색)
  └─ YouTube Data API v3 강의 3개  (Redis TTL 24시간 캐시)
```

---

## 그림 5 — CI/CD 파이프라인

```
개발자 / Claude Code
    │ git push
    ▼
GitHub Pull Request → develop
    │
    ▼
┌─────────────────────────────────────────────────────┐
│  ci.yml 트리거                                       │
│                                                     │
│  Job 1: build-test                                  │
│  ─────────────────────────────────────────────────  │
│  Services 동시 기동:                                 │
│    postgres:16  (localhost:5432)                    │
│    redis:7      (localhost:6379)                    │
│    mongodb:7    (localhost:27017)                   │
│                                                     │
│  Steps:                                             │
│    1. actions/checkout@v4 (fetch-depth: 0)          │
│    2. actions/setup-java@v4 (Java 21, temurin)      │
│    3. ./gradlew build --no-daemon                   │
│       (SPRING_PROFILES_ACTIVE=test)                 │
│    4. 실패 시 → build/reports/tests/ 업로드 후 중단  │
│    5. 성공 시 → build/classes/, jacoco/ 아티팩트 저장│
│                                                     │
│         ▼ 성공해야만 진행                            │
│                                                     │
│  Job 2: sonar  (needs: build-test)                  │
│  ─────────────────────────────────────────────────  │
│  Services: postgres:16 + redis:7 + mongodb:7 동일   │
│                                                     │
│  Steps:                                             │
│    1. Job1 아티팩트 다운로드 (build/)               │
│    2. ./gradlew sonar --no-daemon                   │
│       -Dsonar.qualitygate.wait=true                 │
│    Quality Gate 기준:                               │
│      • 신규 코드 커버리지 ≥ 80%                     │
│      • 코드 중복률 ≤ 3%                             │
│      • Security Hotspot 0개                         │
│      • 버그 · 취약점 0개                            │
│    실패 → exit 1 → PR merge 블로킹                  │
└─────────────────────────────────────────────────────┘
    │
    │ 두 Job 모두 green
    ▼
┌─────────────────────────────────────────────────────┐
│  auto-merge.yml 트리거                               │
│                                                     │
│  조건: PR 브랜치명이 'auto/' 로 시작하는 경우만       │
│    auto/feature/DP-{번호}-{기능명}                  │
│    auto/fix/DP-{번호}-{설명}                        │
│                                                     │
│  gh pr merge --auto --squash                        │
└─────────────────────────────────────────────────────┘
    │
    ▼
develop 브랜치에 Squash Merge 완료
Jira 티켓 → Done 자동 전환


※ feature/DP-{번호} 브랜치는 CI 통과해도 자동 merge 안 됨
   → 팀원 코드 리뷰 후 수동 merge
```

---

## 포인트 적립 정책 (참고)

| 액션 | 포인트 | 중복 방지 |
|------|--------|-----------|
| AI_QUIZ_PASS | 5점 | contentId 기준 (같은 글 재통과 불가) |
| AI_SUMMARY_VIEW | (코드 확인 필요) | — |
| QUESTION_WRITE | (코드 확인 필요) | 없음 (매번 적립) |
| CONTENT_SCRAP | (코드 확인 필요) | contentId 기준 |
| CONTENT_LIKE | (코드 확인 필요) | contentId 기준 |
| DAILY_LOGIN | (코드 확인 필요) | KST 기준 하루 1회 |
