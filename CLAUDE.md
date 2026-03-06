# DevPick Backend — Claude Code Context

> **읽는 순서**: 이 파일(전체 맥락) → `src/main/java/com/devpick/CLAUDE.md` (도메인/DB 상세)

---

## 1. 프로젝트 개요

**DevPick** — 개발자 성장형 통합 플랫폼
> 개발 콘텐츠 탐색 → AI 요약/질문 → 커뮤니티 소통 → 성장 기록/리포트를 하나의 흐름으로 연결

이 레포는 **Spring Boot REST API 서버**다.
- 담당: **홍근** (백엔드 메인), **하영** (백엔드 서브)
- MVP 데드라인: **2026-04-13**
- 현재 스프린트: Sprint 0 (2/24 ~ 3/2) — 환경 세팅

### 시스템 구조 (4개 서버)
```
브라우저 → Nginx → Next.js (프론트, :3000)
                 → Spring Boot (백엔드, :8080) → PostgreSQL (:5432)
                                               → MongoDB (:27017)
                                               → Redis (:6379)
                                               → FastAPI AI 서버 (:8000)
```

---

## 2. 기술 스택

| 구분 | 기술 | 버전 |
|------|------|------|
| 언어 | Java | 21 (LTS) |
| 프레임워크 | Spring Boot | 3.5.x |
| ORM | JPA/Hibernate + QueryDSL | - |
| 빌드 | Gradle | 최신 |
| DB (구조화) | PostgreSQL | 16 (AWS RDS) |
| DB (비정형) | MongoDB | 7 (AI 요약 JSON, 이벤트 로그) |
| 캐시 | Redis | 7 |
| 웹서버 | Nginx | 최신 |
| CI/CD | GitHub Actions | - |
| 인프라 | Docker + AWS EC2 | - |

---

## 3. 패키지 구조 (도메인형)

```
com.devpick
├── domain
│   ├── user          # 사용자/프로필/소셜로그인/토큰
│   │   ├── controller
│   │   ├── service
│   │   ├── repository
│   │   ├── entity
│   │   └── dto
│   ├── content       # 콘텐츠 피드/스크랩/좋아요
│   ├── post          # 커뮤니티 게시글/답변/댓글
│   ├── history       # 학습 히스토리
│   └── report        # 주간 리포트
├── global
│   ├── config        # Spring 설정 (Security, CORS, QueryDSL 등)
│   ├── exception     # 커스텀 예외 클래스
│   ├── response      # ApiResponse<T> 공통 응답 래퍼
│   └── security      # JWT 필터, 인증 처리
```

---

## 4. 자주 쓰는 커맨드

```bash
# 개발 서버 실행
./gradlew bootRun

# 로컬 프로파일 (더미 데이터 포함)
./gradlew bootRun --args='--spring.profiles.active=local'

# 테스트 실행
./gradlew test

# 빌드
./gradlew build

# 전체 서비스 한 번에 (devpick-infra에서)
cd ../devpick-infra && docker-compose up --build
```

---

## 5. 브랜치 / 커밋 / PR 규칙

### 브랜치
```bash
# develop에서 시작 (main 직접 작업 금지)
git checkout develop
git pull origin develop
git checkout -b feature/DP-{티켓번호}-{기능명}

# 예시
git checkout -b feature/DP-177-이메일-회원가입-API
```

| 브랜치 | 용도 |
|--------|------|
| `main` | 배포용. 직접 push 절대 금지 |
| `develop` | 개발 통합. PR 머지 대상 |
| `feature/DP-{번호}-{기능명}` | 기능 개발 (사람이 직접 작업) |
| `auto/feature/DP-{번호}-{기능명}` | Claude Code 자동화 작업. CI 통과 시 develop에 자동 머지 |
| `hotfix/DP-{번호}-{설명}` | 긴급 버그 수정 |

### 커밋 메시지
```
DP-{티켓번호}: {작업 내용}

예: DP-177: 이메일 회원가입 API 개발
```

### PR 제목
```
[DP-{티켓번호}] {설명}

예: [DP-177] 이메일 회원가입 API 구현
```

**머지 조건**: AC 충족 + 팀원 1명 이상 리뷰 승인 + CI 통과

---

## 6. API 공통 포맷 (ADR-003)

**Base URL**: `https://api.devpick.kr/v1`
**인증**: `Authorization: Bearer {access_token}`

### 성공 응답
```json
{
  "success": true,
  "data": { },
  "message": "요청이 성공했습니다"
}
```

### 에러 응답
```json
{
  "success": false,
  "error": {
    "code": "AUTH_001",
    "message": "로그인이 필요합니다",
    "detail": "Access Token이 만료되었습니다"
  }
}
```

### 에러 코드 네임스페이스
| 접두사 | 도메인 |
|--------|--------|
| `AUTH_` | 인증 |
| `USER_` | 사용자 |
| `CONTENT_` | 콘텐츠 |
| `AI_` | AI 기능 |
| `COMMUNITY_` | 커뮤니티 |

### HTTP 상태 코드
| 코드 | 의미 |
|------|------|
| 200 | 성공 |
| 201 | 생성 성공 |
| 400 | 잘못된 요청 |
| 401 | 인증 필요 |
| 403 | 권한 없음 |
| 404 | 찾을 수 없음 |
| 500 | 서버 오류 |

---

## 7. 코드 컨벤션

- **스타일**: Google Java Style Guide, 줄 길이 120자 이하
- **DTO**: `record` 사용 권장
- **예외**: 반드시 커스텀 예외 클래스 사용 (ADR-003 기반)
- **공통 응답**: `ApiResponse<T>` 래퍼 클래스 사용

| 대상 | 규칙 | 예시 |
|------|------|------|
| 클래스 | PascalCase | `UserService` |
| 메서드/변수 | camelCase | `getUserById` |
| 상수 | UPPER_SNAKE_CASE | `MAX_TOKEN_SIZE` |
| 테이블/컬럼 | snake_case | `user_id` |
| URL | kebab-case | `/auth/sign-up` |

---

## 8. API 엔드포인트 전체 목록

### Epic A — 회원/프로필
| Method | Endpoint | 설명 | 인증 | 담당 |
|--------|----------|------|------|------|
| POST | `/auth/signup` | 이메일 회원가입 | X | 하영 (DP-177) |
| POST | `/auth/login` | 이메일 로그인 | X | 하영 (DP-180) |
| POST | `/auth/logout` | 로그아웃 | O | 하영 (DP-185) |
| POST | `/auth/refresh` | Access Token 재발급 | X | 하영 (DP-181) |
| GET | `/auth/github` | GitHub 소셜 로그인 | X | 하영 (DP-183) |
| GET | `/auth/google` | Google 소셜 로그인 | X | 하영 (DP-184) |
| GET | `/users/me` | 내 프로필 조회 | O | 홍근 (DP-187) |
| PUT | `/users/me` | 내 프로필 수정 | O | 홍근 (DP-187) |
| DELETE | `/users/me` | 회원 탈퇴 (soft delete) | O | 홍근 (DP-189) |

### Epic B — 콘텐츠 피드
| Method | Endpoint | 설명 | 인증 | 담당 |
|--------|----------|------|------|------|
| GET | `/contents` | 개인화 피드 (`?page=0&size=10`) | O | 홍근 (DP-204) |
| GET | `/contents/{contentId}` | 글 상세 | O | 홍근 (DP-205) |
| GET | `/contents/search` | 글 검색 | O | 홍근 (DP-210) |
| POST | `/contents/{contentId}/scrap` | 스크랩 | O | 홍근 (DP-207) |
| DELETE | `/contents/{contentId}/scrap` | 스크랩 취소 | O | 홍근 (DP-207) |
| POST | `/contents/{contentId}/like` | 좋아요 | O | 홍근 (DP-208) |
| DELETE | `/contents/{contentId}/like` | 좋아요 취소 | O | 홍근 (DP-208) |

### Epic C — AI 요약
| Method | Endpoint | 설명 | 인증 | 담당 |
|--------|----------|------|------|------|
| GET | `/contents/{contentId}/summary` | 레벨별 AI 요약 조회 | O | 홍근 (DP-221) |
| POST | `/contents/{contentId}/summary/retry` | AI 요약 재시도 | O | 홍근 (DP-221) |

### Epic D — 질문/커뮤니티
| Method | Endpoint | 설명 | 인증 | 담당 |
|--------|----------|------|------|------|
| POST | `/posts` | 질문 작성 + 커뮤니티 동시 게시 | O | 홍근 (DP-229) |
| GET | `/posts` | 게시글 목록 | O | 홍근 |
| GET | `/posts/{postId}` | 게시글 상세 | O | 홍근 |
| PUT | `/posts/{postId}` | 게시글 수정 | O | 홍근 |
| DELETE | `/posts/{postId}` | 게시글 삭제 | O | 홍근 |
| POST | `/posts/refine` | AI 질문 개선 | O | 홍근 (DP-230) |
| GET | `/posts/{postId}/similar` | 유사 질문 조회 | O | 수헌 (DP-235) |
| POST | `/posts/{postId}/ai-answer` | AI 답변 생성 | O | 홍근/수헌 (DP-233) |
| POST | `/posts/{postId}/answers` | 답변 작성 | O | 홍근 (DP-239) |
| PUT | `/posts/{postId}/answers/{answerId}` | 답변 수정 | O | 홍근 |
| DELETE | `/posts/{postId}/answers/{answerId}` | 답변 삭제 | O | 홍근 |
| POST | `/posts/{postId}/answers/{answerId}/adopt` | 답변 채택 | O | 홍근 (DP-239) |
| POST | `/posts/{postId}/answers/{answerId}/comments` | 댓글 작성 | O | 하영 (DP-240) |
| DELETE | `/posts/{postId}/answers/{answerId}/comments/{commentId}` | 댓글 삭제 | O | 하영 |

### Epic E — 학습 히스토리
| Method | Endpoint | 설명 | 인증 | 담당 |
|--------|----------|------|------|------|
| GET | `/history` | 학습 히스토리 조회 | O | 하영 (DP-248) |
| GET | `/history/activity` | 활동 내역 (좋아요 포함) | O | 하영 (DP-249) |

### Epic F — 주간 리포트
| Method | Endpoint | 설명 | 인증 | 담당 |
|--------|----------|------|------|------|
| GET | `/reports/weekly` | 이번 주 리포트 | O | 홍근 (DP-256) |
| GET | `/reports/weekly/{reportId}` | 특정 주 리포트 | O | 홍근 (DP-256) |
| POST | `/reports/weekly/share` | 공유 링크 생성 | O | 홍근 (DP-258) |

---

## 9. 현재 스프린트 — 홍근 담당 티켓

### Sprint 0 (2/24 ~ 3/2) — 환경 세팅
| 티켓 | 작업 |
|------|------|
| DP-136 | devpick-infra 레포 생성 + docker-compose 초안 |
| DP-137 | devpick-backend 레포 생성 + Spring Boot 초기화 |
| DP-140 | GitHub Actions CI 파이프라인 (backend) |
| DP-143 | .env.example 작성 |
| DP-144 | PostgreSQL + MongoDB + Redis docker-compose |
| DP-166 | GitHub-Jira 연동 |
| DP-167 | PR 템플릿 추가 |
| DP-168 | 브랜치 보호 규칙 설정 |
| DP-169 | CLAUDE.md 각 레포에 작성 |

### Sprint 1 (3/3 ~ 3/16) — Epic A/B 핵심
| 티켓 | 작업 |
|------|------|
| DP-171 | Spring Security 설정 |
| DP-172 | CORS 설정 |
| DP-173 | 공통 에러 핸들러 (ADR-003) |
| DP-174 | ApiResponse 공통 응답 포맷 |
| DP-175 | Swagger/OpenAPI 세팅 |
| DP-186/187 | 프로필 조회/수정 API |
| DP-188/189 | 회원 탈퇴 API |
| DP-203/204 | 개인화 피드 조회 API |
| DP-205 | 글 상세 조회 API |
| DP-206~210 | 스크랩/좋아요/검색 API |

---

## 10. ADR 결정 요약

| ADR | 결정 | 상태 |
|-----|------|------|
| ADR-001 | PostgreSQL(구조화) + MongoDB(AI JSON/이벤트) 분리 | **확정** |
| ADR-002 | JWT (Access + Refresh Token) | 제안됨 |
| ADR-003 | API 에러 포맷: `{success, error:{code,message,detail}}` | 제안됨 |
| ADR-005 | Feature Flag: `dp.{영역}.{기능명}` | 미결 |
| ADR-007 | Redis 캐시: summary 7일, feed 10분, report 7일 | 제안됨 |

### Feature Flag 목록
| Flag | 기능 |
|------|------|
| `dp.ai.summary` | AI 요약 |
| `dp.ai.question_refine` | AI 질문 개선 |
| `dp.ai.quiz` | AI 퀴즈 (MVP+) |
| `dp.reports.weekly` | 주간 리포트 |

---

## 11. 테스트 전략

- **도구**: JUnit 5 + Mockito (단위), Spring Boot Test + MockMvc (API)
- **커버리지 목표**: Service 레이어 **70% 이상**
- **CI 트리거**: PR → develop 시 자동 실행
- **테스트 패턴**: given / when / then 구조

```java
@Test
void 이메일_중복_가입_시_예외_발생() {
    // given
    String email = "test@devpick.kr";
    userRepository.save(createUser(email));

    // when & then
    assertThrows(DuplicateEmailException.class,
        () -> userService.signup(email, "password"));
}
```

---

## 12. 포트 정보 (로컬 개발)

| 서비스 | 포트 |
|--------|------|
| Spring Boot (이 서버) | 8080 |
| Next.js 프론트엔드 | 3000 |
| FastAPI AI 서버 | 8000 |
| PostgreSQL | 5432 |
| MongoDB | 27017 |
| Redis | 6379 |

---

## 13. 보안 주의사항

- API Key, DB 비밀번호 등 시크릿은 **절대 코드에 하드코딩 금지**
- `.env` 파일은 `.gitignore`에 포함 (절대 push 금지)
- AI 프롬프트에 개인정보/시크릿 절대 포함 금지
- Claude Code 사용 시 AI 생성 코드도 **PR 올린 사람이 책임**짐
- PR에 AI 사용 여부 반드시 기록

---

## 14. 참고 문서

| 문서 | 내용 |
|------|------|
| `src/main/java/com/devpick/CLAUDE.md` | 도메인/DB 구조 상세 |
| `.env.example` | 환경변수 목록 |
| `.github/PULL_REQUEST_TEMPLATE.md` | PR 작성 양식 |
| Confluence ADR | 기술 결정 기록 |

<\!-- auto-merge 테스트 -->
---

## 15. CI/CD 자동화 구조

### 전체 워크플로 (Claude Code 자동화)

```
/feature-dev DP-177 입력
→ Jira 티켓 자동 읽기 (제목/설명/AC) + In Progress 전환
→ auto/feature/DP-177-email-signup-api 브랜치 생성 (GitHub API)
→ AC 기반 코드 + JUnit 테스트 작성
→ PR 생성 (Jira 링크 자동 삽입 + 코드 자동 리뷰)
→ CI 실행: 빌드 + 테스트 + SonarCloud 분석
→ CI 통과 시 develop 자동 squash 머지 + Jira Done
```

### GitHub Actions 워크플로우

| 파일 | 트리거 | 역할 |
|------|--------|------|
| `ci.yml` | PR → develop | 빌드 · 테스트 · SonarCloud 분석 |
| `auto-merge.yml` | PR → develop (`auto/` 브랜치만) | CI 통과 시 자동 squash 머지 |

### SonarCloud 자동 PR 분석

- PR마다 자동 실행 — 버그 · 취약점 · 코드스멜 · 커버리지 분석
- PR 댓글로 결과 자동 표시 (Quality Gate Pass/Fail)
- 대시보드: https://sonarcloud.io/project/overview?id=Devpick-Org_devpick-backend

### 브랜치별 동작

| 브랜치 | 생성 주체 | 머지 방식 |
|--------|-----------|-----------|
| `auto/feature/DP-{번호}-{기능명}` | Claude Code | CI 통과 시 자동 squash 머지 |
| `feature/DP-{번호}-{기능명}` | 개발자 직접 | PR 확인 후 수동 머지 |
| `hotfix/DP-{번호}-{설명}` | 개발자 직접 | PR 확인 후 수동 머지 |

### Claude Code 스킬 (`/feature-dev`)

```
사용법: /feature-dev DP-177
```

자동으로 하는 것:
- Jira MCP로 티켓 제목 · 설명 · 인수조건(AC) 읽기
- 티켓 상태 자동 전환: To Do → In Progress → Done
- `auto/feature/DP-{번호}-{기능명}` 브랜치 생성
- AC 항목을 테스트 케이스로 변환해 JUnit 5 테스트 작성
- PR 생성 (Jira 링크 자동 삽입)
- PR diff 기반 코드 자동 리뷰
- CI 통과 후 develop 자동 머지

### 실제 워크플로 예시

```
1. Claude Code에서 입력: /feature-dev DP-177

2. 자동 실행:
   - Jira DP-177 읽기 → "이메일 회원가입 API"
   - 브랜치 생성: auto/feature/DP-177-email-signup-api
   - UserController / UserService / UserRepository 작성
   - 이메일 중복 검사, 비밀번호 암호화 등 AC 기반 테스트 작성
   - PR 생성: "[DP-177] 이메일 회원가입 API 구현"

3. CI 자동 실행 (~5분):
   - 빌드 + 테스트 통과
   - SonarCloud Quality Gate Passed

4. develop 자동 squash 머지

5. Jira DP-177 → Done 자동 전환
```

> **팀원 참고**: `feature/` 브랜치로 직접 작업한 PR은 CI 통과 후에도 자동 머지되지 않습니다.
> GitHub에서 리뷰 확인 후 직접 Merge 버튼을 눌러주세요.
