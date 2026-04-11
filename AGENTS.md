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
# 컴파일
./gradlew compileJava compileTestJava --no-daemon

# 전체 빌드 + 테스트 (CI와 동일)
./gradlew build --no-daemon
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
