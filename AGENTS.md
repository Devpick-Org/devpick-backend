# AGENTS.md — Cursor / AI 에이전트용 지침

이 문서는 **이 레포(`devpick-backend`)에서 코드·문서·API 스펙을 수정할 때** 에이전트가 따를 규칙을 정리한다. 상세 워크플로·빌드·Sonar 규칙은 반드시 **[CLAUDE.md](./CLAUDE.md)** 를 함께 참고한다.

---

## 1. 읽기 순서

1. **AGENTS.md** (본 문서) — 빠른 체크리스트
2. **[CLAUDE.md](./CLAUDE.md)** — 빌드/테스트/PR/Sonar/브랜치·커밋 규칙
3. **`src/main/java/com/devpick/CLAUDE.md`** — 패키지·도메인 세부 (있는 경우)

---

## 2. OpenAPI / API 스펙 동기화

- **OpenAPI 정본**: [`devpick-api-spec.json`](./devpick-api-spec.json) (OpenAPI 3.0.3) — 이 파일만 유지한다.

**REST DTO·컨트롤러 응답/요청 스키마를 바꾼 경우** 반드시 `devpick-api-spec.json`의 `components.schemas` 및 해당 `paths` 항목을 같은 PR에서 갱신한다.

최근 확정 예시 (DP-300):

- **AI 퀴즈** `GET /contents/{contentId}/quiz`: 응답 `questions[]`에 `type` (`multiple_choice` | `short_answer`) 포함. 문제 순서는 객관식 → 주관식 고정.
- **퀴즈 제출** `POST .../quiz/submit`: `QuizSubmitRequest`는 기존과 동일; 주관식은 프론트 자기 채점 후 `score`/`passed` 반영.
- **질문 개선** `POST /posts/refine`: 요청에 선택 필드 `postId` (UUID). 전달 시 refine 결과가 `ai_questions`에 저장되어 AI 답변 생성 시 refined 문구를 사용.

---

## 3. 브랜치·커밋·PR

- 브랜치: `feature/DP-{번호}-...` 또는 `auto/fix/DP-{번호}-...` (조직 규칙에 따름)
- 커밋 메시지: `DP-{번호}: {한 줄 설명}`
- PR 전 로컬 검증: [CLAUDE.md](./CLAUDE.md)의 `./gradlew build --no-daemon` 및 Sonar 체크리스트 준수

---

## 4. 빌드·테스트 (에이전트가 직접 실행)

에이전트는 **사용자에게 명령만 안내하지 말고**, 가능하면 아래를 레포 루트에서 실행한다.

```bash
# Cursor/일부 환경에서는 GRADLE_USER_HOME이 샌드박스 캐시로 잡혀 Gradle이 멈추거나
# "immutable workspace ... metadata file" 오류가 난다. 아래처럼 매번 ~/.gradle 로 고정한다.
GRADLE_USER_HOME="$HOME/.gradle" ./gradlew compileJava compileTestJava --no-daemon
GRADLE_USER_HOME="$HOME/.gradle" ./gradlew build --no-daemon
```

- 테스트 실패 시: `build/reports/tests/test/index.html` 확인
- `DevpickApplicationTests.contextLoads()`는 환경 변수 **`CI=true`** 일 때만 실행된다(GitHub Actions 기본값). 로컬 `./gradlew build`는 DB 없이도 통과하고, 통합 스모크는 CI에서 검증된다.

---

## 5. SonarCloud (PR 머지 전 필수)

- **신규 코드 커버리지 ≥ 80%** (Quality Gate). 부족하면 해당 클래스/분기용 단위 테스트를 추가한다.
- 상세·중복·보안: [CLAUDE.md](./CLAUDE.md) Sonar 섹션 참고.

## 6. 코드 스타일 (요약)

- 불필요한 대규모 리팩터 금지; 요청 범위 안에서만 수정
- 시크릿·`.env` 커밋 금지 (`.gitignore` 준수)
- 새 public API는 OpenAPI와 테스트를 함께 맞출 것

---

## 7. 관련 문서

| 문서 | 용도 |
|------|------|
| [CLAUDE.md](./CLAUDE.md) | 빌드, Sonar, PR, 보안 핫스팟 |
| [devpick-api-spec.json](./devpick-api-spec.json) | Trace REST OpenAPI 정본 |
| [docs/](./docs/) | 아키텍처·연동 메모 |

---

## 8. EC2 배포: 8080 오픈 · 프론트 연동 · 환경변수 (운영 맥락)

### 8.1 보안 그룹에서 8080 열기

1. AWS 콘솔 → **EC2** → 인스턴스 선택 → **보안** 탭 → 보안 그룹 링크 클릭  
2. **인바운드 규칙 편집** → **규칙 추가**  
   - 유형: **사용자 지정 TCP**  
   - 포트 범위: **8080**  
   - 소스: 프론트가 브라우저에서 접근하는 경우 보통 **`0.0.0.0/0`** (전체 공개) 또는 팀/사무실 IP만 허용  
3. 저장  

로컬에서 `curl http://퍼블릭IP:8080/actuator/health` 또는 `/health` 등으로 응답 오는지 확인한다.

### 8.2 HTTP vs HTTPS · 프론트 base URL

- 프론트 env의 **백엔드 base URL**은 브라우저가 호출할 수 있는 주소다.  
- **운영(HTTPS, Nginx → Spring 8080, sslip.io + Elastic IP)** 에서 쓰는 공개 API 베이스는 아래 **한 줄**을 팀·프론트에 전달한다 (경로 없이 origin만).

```text
https://3-39-96-126.sslip.io
```

- **HTTPS만 쓰는 사이트**에서 `http://` API를 부르면 브라우저가 **Mixed Content**로 막을 수 있다. 위 주소는 HTTPS이므로 프론트도 HTTPS 배포 시 함께 맞춘다.  
- EC2 **퍼블릭 IP가 바뀌면** sslip 호스트(`3-39-96-126` 부분)와 Let’s Encrypt 인증서·Nginx `server_name`도 같이 갱신해야 한다. **Elastic IP**로 공인 IP를 고정해 두었다.  
- 백엔드의 **`FRONTEND_URL`** 은 OAuth 리다이렉트·CORS와 맞춰야 한다. 프론트가 배포된 실제 origin으로 맞춘다. `REPLACE_ME`면 OAuth·리다이렉트가 깨질 수 있다.

### 8.3 운영 시크릿은 레포에 넣지 않는다

**비밀번호·JWT·API 키는 AGENTS.md나 Git에 적지 않는다.** 실제 값은 EC2의 `~/devpick-backend/.env` / `~/devpick-ai/.env` 등에만 둔다.

에이전트가 “운영 DB/Redis 키가 뭔지”를 알아야 할 때는 **변수 이름**만 참고한다:

| 구분 | 변수 (이름만) |
|------|----------------|
| DB (Spring) | `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` |
| AI 레포 (Python) | `DATABASE_URL` (또는 위와 동일 RDS) |
| Redis | `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` |
| 인증 | `JWT_SECRET` |
| AI 내부 API | `AI_SERVER_INTERNAL_KEY` (백엔드 `AI_SERVER_INTERNAL_KEY`와 동일 값) |
| AWS (Bedrock 등) | `AWS_REGION`, `BEDROCK_MODEL`, `BEDROCK_EMBEDDING_MODEL` |
| 메일·OAuth | `RESEND_API_KEY`, `GITHUB_*`, `GOOGLE_*`, `FRONTEND_URL` |

값 변경·유출 시에는 RDS 비밀번호·JWT·내부 키 순으로 **로테이션**한다.

---

## 9. 로컬 프론트 + 로컬 백엔드 연동 (테스트)

운영 API(`api.devpick.kr`)를 쓰면 OAuth `redirect_uri` 가 운영 프론트로 잡혀 **localhost 소셜 로그인**이 끝까지 맞지 않을 수 있다. 로컬에서 끝까지 보려면 아래를 맞춘다.

1. **백엔드**: 레포 루트에서 `.env.example` → `.env` 복사 후 `SPRING_PROFILES_ACTIVE=local`, `FRONTEND_URL=http://localhost:3000` (또는 실제 쓰는 포트), `GITHUB_*` / `GOOGLE_*` 채움. GitHub·Google 개발자 콘솔에 콜백 등록:  
   `http://localhost:3000/auth/github/callback`, `http://localhost:3000/auth/google/callback`.
2. **백엔드 기동**: `GRADLE_USER_HOME="$HOME/.gradle" ./gradlew bootRun --no-daemon` (또는 팀 표준). 기본 포트 8080 가정.
3. **프론트 (`devpick-frontend`)**: 기존 `.env.local` 을 **지우지 않고**, 여기에 한 줄 추가하거나 `npm run dev:local` 사용.  
   `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/v1`  
   샘플: 레포의 `env.local.sample` 참고.
4. 브라우저는 **`http://localhost:3000`** 으로 접속 (OAuth 리다이렉트·CORS·`FRONTEND_URL` 과 일치).

위는 **로컬 검증용**이며, 운영 배포·EC2 환경 변수 설명은 §8과 동일하게 유지한다.
