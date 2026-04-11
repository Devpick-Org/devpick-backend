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
| DB (AI 문서) | DynamoDB | (AWS) |
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
| **A+. 포인트/배지** | 학습 행동별 포인트 적립, 배지 잠금 해제, 누적 포인트/스트릭/내역 조회 | DP-269 | ✅ 완료 |
| **B. 콘텐츠 피드** | 태그 기반 개인화 피드, 글 상세, 스크랩/좋아요, 검색, 태그 기반 추천 | DP-204, 205, 207, 208, 210 | ✅ 완료 |
| **B+. 콘텐츠 수집** | Stack Overflow API + Velog GraphQL 크롤러, POST /internal/contents 수신 | DP-199~202, 289 | ✅ 완료 |
| **C. AI 요약** | 레벨별 AI 요약 (입문/주니어/미들/시니어), Redis+DynamoDB 캐시·재시도 | DP-221 | ✅ 완료 |
| **C+. AI 퀴즈** | 콘텐츠 기반 레벨별 퀴즈 생성, 시도 이력 저장, 통과 시 포인트+히스토리 | — | ✅ 완료 |
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
| DP-221 | AI 요약 (Redis→DynamoDB→FastAPI 캐시·재시도) | 홍근 |
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
| ADR-001 | PostgreSQL (구조화) + DynamoDB (AI 요약·퀴즈·리포트 인사이트 등 문서) |
| ADR-002 | JWT Access Token + Refresh Token (HttpOnly Cookie) |
| ADR-003 | API 에러 포맷: `{success, error:{code, message, detail}}` |
| ADR-007 | Redis 캐시: AI 요약/퀴즈 7일, 피드 10분, 리포트 7일 |

### 스프린트 운영
- **주기**: 1~2주 단위
- **정기 미팅**: 매주 월요일 13~14시
- **회고**: KPT (Keep/Problem/Try) → Try 항목을 다음 스프린트 Jira 티켓으로 전환

---

## 6. DB·스키마 (PostgreSQL / DynamoDB / Redis)

상세 스키마·엔티티 경로·Redis 키 패턴은 **[docs/table-definition.md](docs/table-definition.md)** 를 단일 기준으로 본다.

- ERDCloud용 DDL: [docs/erdcloud-postgres.sql](docs/erdcloud-postgres.sql), [docs/erdcloud-postgres-friendly.sql](docs/erdcloud-postgres-friendly.sql)
- 테이블 한글 라벨: [docs/erdcloud-korean-labels.md](docs/erdcloud-korean-labels.md)

---

## 7. API·내부 연동

- 공개 REST 스펙(OpenAPI JSON): **[devpick-api-spec.json](devpick-api-spec.json)**
- Spring ↔ FastAPI 경로·`X-Internal-Key`: **[docs/통신.md](docs/통신.md)**
- 엔드포인트 요약·공통 응답 포맷: 루트 **[CLAUDE.md](CLAUDE.md)** §5, §9

---

## 8. CI/CD

GitHub Actions·SonarCloud·브랜치 전략: 루트 **[CLAUDE.md](CLAUDE.md)** §15 (및 §4 브랜치/PR 규칙).

---

## 9. 앞으로 할 일

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
