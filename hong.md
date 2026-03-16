# 홍근 To-Do

> 마지막 업데이트: 2026-03-16

---

## 🔴 지금 당장 (오늘)

### 1. Railway 테스트 배포 (DP-290) — 브라우저 작업

**순서:**
1. Railway 회원가입/로그인: https://railway.app
2. 새 프로젝트 생성 → GitHub 레포 연결 (`Devpick-Org/devpick-backend`)
3. 서비스 추가:
   - Spring Boot (GitHub 레포에서 자동 감지 — `Dockerfile` 있음)
   - PostgreSQL (Railway 플러그인)
   - MongoDB (Railway 플러그인)
   - Redis (Railway 플러그인)
4. Spring Boot 서비스 환경변수 설정:
   ```
   # 데이터베이스 (Railway PostgreSQL 연결 정보로 교체)
   DB_HOST=<Railway PostgreSQL host>
   DB_PORT=5432
   DB_NAME=railway
   DB_USERNAME=postgres
   DB_PASSWORD=<Railway 자동생성>

   # MongoDB (Railway MongoDB 연결 정보로 교체)
   MONGO_HOST=<Railway MongoDB host>
   MONGO_PORT=27017
   MONGO_DB=devpick
   MONGO_USERNAME=<Railway 자동생성>
   MONGO_PASSWORD=<Railway 자동생성>

   # Redis (Railway Redis 연결 정보로 교체)
   REDIS_HOST=<Railway Redis host>
   REDIS_PORT=6379
   REDIS_PASSWORD=<Railway 자동생성>

   # JWT
   JWT_SECRET=<랜덤 64자 이상 문자열>

   # OAuth
   GITHUB_CLIENT_ID=<GitHub OAuth App client id>
   GITHUB_CLIENT_SECRET=<GitHub OAuth App client secret>
   GOOGLE_CLIENT_ID=<Google OAuth client id>
   GOOGLE_CLIENT_SECRET=<Google OAuth client secret>
   FRONTEND_URL=<보민 프론트엔드 URL>

   # 이메일 인증 (Gmail App Password 필요)
   MAIL_USERNAME=<Gmail 계정>
   MAIL_PASSWORD=<Gmail App Password>

   # CORS (프론트엔드 Railway/Vercel URL)
   CORS_EXTRA_ORIGIN=<보민 프론트엔드 URL>

   # AI 서버 (없으면 임시 mock URL 또는 빈값)
   AI_SERVER_URL=http://localhost:8000

   # PORT는 Railway가 자동으로 설정함 — 직접 입력 불필요
   ```
5. Deploy Hook URL 복사 → GitHub Secret에 저장:
   - Railway: Service → Settings → Deploy Hooks → URL 복사
   - GitHub: `Devpick-Org/devpick-backend` → Settings → Secrets → `DEPLOY_STAGING_WEBHOOK_URL`
6. `GET https://<railway-url>/api/health` → `{"success": true}` 확인
7. 보민에게 Railway URL 전달

### 2. Gmail App Password 발급 (이메일 인증 필요 시)
1. Google 계정 → 보안 → 2단계 인증 활성화
2. 앱 비밀번호 생성 → "메일", "기타(devpick)" 선택
3. 생성된 16자리 비밀번호 → Railway 환경변수 `MAIL_PASSWORD`에 입력

---

## 🟡 이번 주

### 3. GitHub Actions CD Secret 설정
Railway 배포 후:
- `DEPLOY_STAGING_WEBHOOK_URL` — Railway deploy hook URL
- (나중에 프로덕션 준비되면) `DEPLOY_PRODUCTION_WEBHOOK_URL`

---

## 🟢 나중에 (최종 배포 전)

| # | 티켓 | 내용 | 비고 |
|---|------|------|------|
| 4 | DP-280 | v0.1.0 배포 기록 작성 | Railway 배포 완료 후 |
| 5 | DP-273~278 | AWS EC2 프로덕션 환경 | MVP 테스트 완료 후 |

> EC2 배포 시 `cd.yml` 하단 주석처리된 `deploy-ssh` job 사용.

---

## ✅ 완료된 것 (건드리지 말 것)

| 티켓 | 내용 | PR |
|------|------|----|
| DP-290 | Railway 배포 준비 — docker 프로필 mail/port/cors 설정 추가 | #65 (CI 대기) |
| DP-189 | 탈퇴 후 재가입 정책 (7일 복구 창), POST /auth/recover | #64 ✅ |
| DP-187 | PUT /users/me 태그 버그 수정 | #60 ✅ |
| DP-176 | 소셜 로그인 응답 SocialLoginResponse 분리, isNewUser 제거 | #63 ✅ |
| DP-183 | GitHub 이메일 비공개 사용자 fallback 처리 | #61 ✅ |
| DP-256, DP-258 | 주간 리포트 API | ✅ |
| DP-246~249 | 학습 히스토리 API (하영) | #59 ✅ |
| DP-229~240 | 커뮤니티 API | ✅ |
| DP-204~210 | 콘텐츠 피드 API | ✅ |
| DP-177~196 | 회원/인증 API | ✅ |
