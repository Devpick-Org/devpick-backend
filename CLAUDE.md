# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **읽는 순서**: 이 파일(전체 맥락) → `src/main/java/com/devpick/CLAUDE.md` (도메인/DB 상세)

---

## ⚠️ Claude Code 작업 필수 규칙

### 커밋 메시지
- `authored-by claude`, `co-authored-by claude`, session URL, 기타 Claude 관련 내용 **절대 포함 금지**
- 형식: `DP-{티켓번호}: {작업 내용}` (예: `DP-177: 이메일 회원가입 API 개발`)

### PR 생성 전 필수 확인 절차

**PR을 올리기 전에 반드시 아래 순서대로 로컬 검증을 완료해야 한다.**

#### 1단계: 빌드 + 테스트 통과 확인
```bash
./gradlew build --no-daemon
```
- 빌드 실패 또는 테스트 실패 시 PR 생성 금지 — 원인 수정 후 재시도
- 실패 원인은 `build/reports/tests/` 확인

#### 2단계: SonarCloud Quality Gate 사전 점검 (코드 리뷰)
PR 생성 전 **아래 항목을 코드에서 직접 눈으로 확인**한다:

| 항목 | 확인 방법 |
|------|----------|
| **신규 코드 커버리지 ≥ 80%** | 새 Service/Controller 메서드마다 테스트 케이스 존재 여부 |
| **코드 중복률 ≤ 3%** | 복붙한 코드 없는지, 공통 추상 클래스로 추출 가능한지 |
| **Security Hotspot** | `csrf().disable()` 등에 `// NOSONAR java:Sxxxx` 주석 추가 여부 |
| **버그/취약점 0개** | Null 체크, 하드코딩 비밀번호, SQL 인젝션 없는지 |

#### 3단계: PR 생성
위 두 단계 모두 통과한 경우에만 PR을 생성한다.

> **왜 중요한가**: CI가 실패하면 auto-merge가 블로킹되고, SonarCloud Quality Gate 실패 시 PR이 develop에 머지되지 않는다. 로컬 검증 없이 PR을 올리면 반복 수정 커밋이 생겨 히스토리가 지저분해진다.

### SonarCloud Quality Gate — 코드 작성 시 필수 준수

모든 PR은 SonarCloud Quality Gate를 통과해야 머지 가능. **코드 작성 전/후 반드시 아래 기준 확인.**

| 항목 | 기준 | 자주 걸리는 원인 |
|------|------|-----------------|
| **코드 중복률** | **≤ 3%** | 유사한 클래스 여러 개 생성, 복붙 코드 |
| **Security Hotspot** | 미검토 0개 | `csrf().disable()` → `// NOSONAR java:S4502` 필요 |
| **버그** | 0개 | Null 체크 누락, 잘못된 타입 사용 |
| **취약점** | 0개 | 하드코딩된 비밀번호, SQL 인젝션 등 |

**중복 코드 방지 규칙:**
1. 이미 있는 베이스 클래스 새로 만들지 말 것 — `global/entity/BaseTimeEntity.java` 존재, `BaseEntity` 생성 금지
2. 테스트 셋업 코드(MockMvc, ObjectMapper 초기화)가 여러 파일에 반복되면 감지됨
3. 새 클래스 작성 전 `global/`, `common/` 디렉토리에 동일 역할 클래스 없는지 확인

**Security Hotspot 처리:**
```java
.csrf(AbstractHttpConfigurer::disable) // NOSONAR java:S4502
```

### CI/CD 자동 머지 — 필수 통과 조건

`auto/feature/DP-*` 브랜치는 아래 **두 GitHub Actions job이 모두 초록색**이어야 자동 머지됨:

| Job | 파일 | 블로킹 조건 |
|-----|------|------------|
| `Build & Test` | `ci.yml` / `build-test` job | 빌드 실패 또는 테스트 실패 시 블로킹 |
| `SonarCloud Quality Gate` | `ci.yml` / `sonar` job | Quality Gate `ERROR`/`WARN` 시 exit 1로 블로킹 |

**SonarCloud Quality Gate 실패 원인 TOP 3:**
1. **신규 코드 커버리지 < 80%** → 새 Service/Controller에 단위 테스트 반드시 작성
2. **코드 중복률 > 3%** → 공통 추상 클래스 재사용, 복붙 금지
3. **Security Hotspot 미검토** → `// NOSONAR java:Sxxxx` 주석 추가

**GitHub branch protection 설정 (수동):**
`develop` 브랜치 보호 규칙에서 Required status checks:
- `Build & Test`
- `SonarCloud Quality Gate`
두 항목 모두 체크되어 있어야 auto-merge가 Quality Gate를 기다림.

---

## 1. 프로젝트 개요

**DevPick** — 개발자 성장형 통합 플랫폼
> 개발 콘텐츠 탐색 → AI 요약/질문 → 커뮤니티 소통 → 성장 기록/리포트를 하나의 흐름으로 연결

이 레포는 **Spring Boot REST API 서버**다.
- 담당: **홍근** (백엔드 메인), **하영** (백엔드 서브)
- MVP 데드라인: **2026-04-13**
- 현재 스프린트: Sprint 1 (3/3 ~ 3/16) — Epic A/B 핵심 API

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
| 프레임워크 | Spring Boot | 3.5.11 (Spring Framework 6.x) |
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
│   ├── content       # 콘텐츠 피드/스크랩/좋아요/AI요약 (구현 완료)
│   │   └── collector/    # CollectedContent, NormalizedContentDto, StackOverflowCollector
│   │   └── document/     # AiSummaryDocument (MongoDB)
│   │   └── client/       # AiServerClient (FastAPI 통신)
│   ├── community     # 게시글/답변/AI질문개선 (구현 완료)
│   │   └── client/       # AiQuestionClient (FastAPI /refine 호출)
│   └── report        # 주간 리포트 + 학습 히스토리 (구현 완료)
│                     # ※ history 패키지는 설계상 분리 예정이나 현재 report 하위에 있음
├── global
│   ├── common
│   │   ├── exception     # DevpickException, ErrorCode enum, GlobalExceptionHandler
│   │   └── response      # ApiResponse<T> record
│   ├── entity            # BaseTimeEntity, BaseCreatedEntity
│   ├── config            # SecurityConfig, CorsConfig, SwaggerConfig, WebClientConfig
│   └── security          # JwtTokenProvider, JwtAuthenticationFilter
```

---

## 4. 브랜치 / 커밋 / PR 규칙

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

## 5. API 공통 포맷 (ADR-003)

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

## 6. 코드 컨벤션

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

## 7. 핵심 코드 패턴

### 예외 처리
모든 비즈니스 예외는 `DevpickException`에 `ErrorCode` enum을 넘겨 던진다. `ErrorCode`는 HTTP 상태 코드, 에러 코드 문자열, 메시지를 함께 보유한다.

```java
// 새 에러 코드 추가 위치: global/common/exception/ErrorCode.java
AUTH_DUPLICATE_EMAIL(HttpStatus.CONFLICT, "AUTH_004", "이미 사용 중인 이메일입니다."),

// 예외 던지기
throw new DevpickException(ErrorCode.AUTH_DUPLICATE_EMAIL);
```

`GlobalExceptionHandler`가 `DevpickException`을 잡아 `ApiResponse.fail(code, message)`로 변환한다. 새 예외 타입을 추가할 때는 `GlobalExceptionHandler`도 함께 수정하지 않아도 된다 — `DevpickException` + `ErrorCode` 조합으로 처리된다.

### 컨트롤러 응답
```java
// 성공 응답 (data 포함)
return ApiResponse.ok(authService.signup(request));

// 성공 응답 (data 없음)
return ApiResponse.ok(null);   // 또는 ApiResponse.ok()
```

컨트롤러에서 HTTP 상태 코드는 `@ResponseStatus`로 선언한다 (`@ResponseStatus(HttpStatus.CREATED)`).

### 엔티티 기본 구조
모든 JPA 엔티티는 `BaseEntity`를 상속한다. `BaseEntity`는 `id(UUID)`, `createdAt`, `updatedAt`을 포함한다. 엔티티 생성자는 `protected`로 막고, `static factory method` 또는 `@Builder`를 사용한다.

---

## 8. 테스트 패턴

테스트 프로파일은 `application-test.yml`을 사용하며, `@ActiveProfiles("test")` 없이 `src/test/resources`에서 자동 로드된다.

### 서비스 단위 테스트 (`@ExtendWith(MockitoExtension.class)`)
```java
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @InjectMocks private AuthService authService;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("이메일 중복 - AUTH_DUPLICATE_EMAIL 예외가 발생한다")
    void signup_duplicateEmail_throwsException() {
        // given
        given(userRepository.existsByEmail(any())).willReturn(true);
        // when & then
        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_DUPLICATE_EMAIL));
    }
}
```

### 컨트롤러 통합 테스트 (`@WebMvcTest`)
```java
@WebMvcTest(AuthController.class)
class AuthControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // 컨트롤러에 주입된 서비스는 모두 @MockitoBean으로 등록 (Spring Boot 3.4+)
    @MockitoBean private AuthService authService;
    @MockitoBean private EmailVerificationService emailVerificationService;

    @Test
    @WithMockUser                        // Spring Security 인증 우회
    void signup_success() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .with(csrf())    // CSRF 토큰 (SecurityConfig에서 disable해도 WebMvcTest는 필요)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }
}
```

**주의**: `@WebMvcTest`에서 컨트롤러에 주입된 모든 서비스를 `@MockitoBean`으로 등록하지 않으면 컨텍스트 로딩 실패.

---

## 9. API 엔드포인트 전체 목록

### Epic A — 회원/프로필
| Method | Endpoint | 설명 | 인증 | 담당 |
|--------|----------|------|------|------|
| POST | `/auth/signup` | 이메일 회원가입 | X | 하영 (DP-177) |
| POST | `/auth/email/send` | 이메일 인증 코드 발송 | X | 하영 (DP-178) |
| POST | `/auth/email/verify` | 이메일 인증 코드 검증 | X | 하영 (DP-178) |
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
| GET | `/contents/{contentId}/recommendations` | 태그 기반 추천 콘텐츠 | O | 홍근 |

### Internal API (AI 레포 전용, Nginx에서 외부 차단 예정)
| Method | Endpoint | 설명 | 인증 | 담당 |
|--------|----------|------|------|------|
| POST | `/internal/contents` | AI 레포가 수집한 콘텐츠 일괄 수신 → PostgreSQL 저장 | X(내부) | 홍근 (DP-289) |

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

## 10. 구현 완료 도메인 현황

| 도메인 | 구현 상태 | 주요 티켓 |
|--------|-----------|----------|
| user (인증/프로필) | ✅ 완료 | DP-177~189, DP-196 |
| content (피드/스크랩/좋아요/검색) | ✅ 완료 | DP-204~210, DP-289 |
| content (AI 요약) | ✅ 완료 | DP-221 |
| community (게시글/답변/AI질문개선) | ✅ 완료 | DP-229~240 |
| report (주간 리포트) | ✅ 완료 | DP-256~258 |

> 티켓별 상세 진행 상황은 Jira (프로젝트: DevPick) 참고

---

## 11. ADR 결정 요약

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

## 12. 테스트 전략

- **도구**: JUnit 5 + Mockito (단위), Spring Boot Test + MockMvc (API)
- **커버리지 목표**: Service 레이어 **70% 이상**
- **CI 트리거**: PR → develop / PR → main 시 자동 실행
- **테스트 패턴**: given / when / then 구조

---

## 13. 포트 정보 (로컬 개발)

| 서비스 | 포트 |
|--------|------|
| Spring Boot (이 서버) | 8080 |
| Next.js 프론트엔드 | 3000 |
| FastAPI AI 서버 | 8000 |
| PostgreSQL | 5432 |
| MongoDB | 27017 |
| Redis | 6379 |

---

## 14. 참고 문서

| 문서 | 내용 |
|------|------|
| `src/main/java/com/devpick/CLAUDE.md` | 도메인/DB 구조 상세 |
| `TRB.md` | 트러블슈팅 로그 전체 |
| `hong.md` | 팀원 하영 온보딩 가이드 |
| `.env.example` | 환경변수 목록 |
| `.github/PULL_REQUEST_TEMPLATE.md` | PR 작성 양식 |
| Confluence ADR | 기술 결정 기록 |

<!-- auto-merge 테스트 -->
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
| `ci.yml` | PR → develop, PR → main | 빌드 · 테스트 · SonarCloud 분석 |
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

> **팀원 참고**: `feature/` 브랜치로 직접 작업한 PR은 CI 통과 후에도 자동 머지되지 않습니다.
> GitHub에서 리뷰 확인 후 직접 Merge 버튼을 눌러주세요.

---

## 16. 트러블슈팅 로그

> **규칙**: 문제를 직면하면 해결 즉시 `TRB.md`에 기록한다. 같은 문제를 두 번 겪지 않는다.
>
> 전체 기록: [`TRB.md`](TRB.md)