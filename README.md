# devpick-backend

> **Trace** | 개발자 성장 플랫폼 Spring Boot API 서버 · 2026 캡스톤 백엔드<br>
> 전체 프로젝트 소개 → [Devpick-Org](https://github.com/Devpick-Org)

---

## 🛠️ 기술 스택

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.5.11-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL_16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![DynamoDB](https://img.shields.io/badge/DynamoDB-4053D6?style=for-the-badge&logo=amazondynamodb&logoColor=white)
![Redis](https://img.shields.io/badge/Redis_7-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![AWS](https://img.shields.io/badge/AWS-232F3E?style=for-the-badge&logo=amazonwebservices&logoColor=white)
![Nginx](https://img.shields.io/badge/Nginx-009639?style=for-the-badge&logo=nginx&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white)
![SonarCloud](https://img.shields.io/badge/SonarCloud-F3702A?style=for-the-badge&logo=sonarcloud&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white)
![Swagger](https://img.shields.io/badge/Swagger-85EA2D?style=for-the-badge&logo=swagger&logoColor=black)
![JUnit5](https://img.shields.io/badge/JUnit_5-25A162?style=for-the-badge&logo=junit5&logoColor=white)

| 구분 | 기술 |
|------|------|
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 3.5.11 |
| 빌드 | Gradle |
| ORM | JPA / Hibernate + QueryDSL |
| DB | PostgreSQL 16 (AWS RDS) |
| 캐시 | Redis 7 (AWS ElastiCache) |
| 비정형 DB | DynamoDB (AI 요약, 퀴즈, 리포트 인사이트) |
| 인프라 | AWS EC2, Docker, Nginx |
| CI/CD | GitHub Actions, SonarCloud |
| 인증 | JWT (Access + Refresh Token), OAuth2 (GitHub, Google) |
| 테스트 | JUnit 5, Mockito, JaCoCo |
| API 문서 | Swagger / OpenAPI |

---

## 🏗️ 시스템 구조

```
브라우저
  └─ Nginx (TLS)
       ├─ Next.js 프론트엔드 (:3000)
       └─ Spring Boot API 서버 (:8080)
              ├─ PostgreSQL (AWS RDS :5432)
              ├─ Redis (AWS ElastiCache :6379)
              ├─ DynamoDB (AWS)
              └─ FastAPI AI 서버 (:8000)
```

---

## 📁 프로젝트 구조

```
src/main/java/com/devpick
├── domain
│   ├── user        # 회원가입 / 로그인 / 소셜인증 / 프로필
│   ├── content     # 콘텐츠 피드 / 스크랩 / AI 요약 / AI 퀴즈 / 맞춤 추천
│   ├── community   # 게시글 / 답변 / AI 질문 개선 / AI 답변 / 첨부파일
│   ├── report      # 주간 리포트 / 학습 히스토리
│   ├── point       # 포인트 적립 / 배지
│   ├── job         # 채용 공고 / 북마크 / 모의면접
│   ├── resume      # 이력서 관리
│   └── trend       # 트렌드 분석
└── global
    ├── common      # 예외 처리 / 공통 응답
    ├── config      # Security / CORS / Swagger / S3 / DynamoDB / Async / Jackson / WebClient
    ├── controller  # 헬스 체크
    ├── entity      # 공통 Base 엔티티
    ├── security    # JWT 필터 / 토큰 프로바이더
    ├── storage     # 파일 스토리지
    └── util        # 유틸리티
```

---

## ✨ 주요 기능

| 도메인 | 기능 |
|--------|------|
| 인증 | 이메일 회원가입·로그인, GitHub / Google OAuth2, JWT 갱신 |
| 프로필 | 내 프로필 조회·수정, 프로필 이미지 업로드, 타인 공개 프로필 조회, 회원 탈퇴 |
| 콘텐츠 | 관심 태그 기반 개인화 피드, 스크랩, 좋아요, 스크랩 목록 조회, 검색, 맞춤 추천 (콘텐츠·유튜브·서적) |
| AI | 레벨별 AI 요약, AI 퀴즈·퀴즈 히스토리, AI 질문 개선, AI 답변 생성 |
| 커뮤니티 | 질문 게시글, 답변 채택, 댓글, 첨부파일 업로드, 유사 질문 조회, AI 답변 생성 |
| 리포트 | 주간 학습 리포트 생성·공유, 학습 히스토리 |
| 포인트 | 학습 행동별 포인트 적립, 배지 시스템 |
| 채용 | 채용 공고 수집·매칭, 북마크·북마크 목록 조회, 면접 Q&A, 모의면접, 부족 역량 보완 추천 |
| 이력서 | 이력서 관리, 문서 파싱, AI 보강 |
| 트렌드 | 에코시스템 트렌드 (부트캠프·개발행사·개발동아리), 트렌딩 키워드, 주간 상위 콘텐츠 집계 |

---

## 🚀 Getting Started

### 사전 요구사항

- Java 21
- Docker & Docker Compose
- `.env` 파일 (`.env.example` 참고)

### 로컬 실행 (PostgreSQL + Redis + 앱)

```bash
git clone https://github.com/Devpick-Org/devpick-backend.git
cd devpick-backend
cp .env.example .env   # 환경변수 설정
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
```

### DB / 캐시만 실행 (앱은 IDE에서 직접)

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d postgres redis
./gradlew bootRun
```

### 빌드 & 테스트

```bash
./gradlew build --no-daemon        # 빌드 + 전체 테스트
./gradlew test --no-daemon         # 테스트만
./gradlew jacocoTestReport         # 커버리지 리포트 생성
```

---

## 📖 API 문서

로컬 실행 후: `http://localhost:8080/swagger-ui/index.html`

---

## ⚙️ CI/CD

| Job | 트리거 | 설명 |
|-----|--------|------|
| Build & Test | PR → developV2 | Gradle 빌드 + JUnit 테스트 |
| SonarCloud | PR → developV2 | 커버리지·코드 품질 분석 (non-blocking) |
| Auto Merge | `auto/` 브랜치 PR | CI 통과 시 developV2 자동 squash 머지 |
| Deploy | push → developV2 | EC2 SSH 자동 배포 |
| Backend CD | 수동 트리거 | GHCR Docker 이미지 빌드 |

---

## 🔀 브랜치 전략

| 브랜치 | 용도 |
|--------|------|
| `main` | 배포용 |
| `develop` | MVP |
| `developV2` | MVP 이후 통합 브랜치 |
| `feature/DP-{번호}-{기능명}` | 기능 개발 |
| `fix/DP-{번호}-{설명}` | 버그 수정 |
| `auto/feature/DP-{번호}-{기능명}` | 기능 개발 — CI 통과 시 자동 머지 |
| `auto/fix/DP-{번호}-{설명}` | 버그 수정 — CI 통과 시 자동 머지 |

---

## 👥 팀

<table>
  <tr>
    <td align="center" width="180">
      <a href="https://github.com/khg9859">
        <img src="https://github.com/khg9859.png" width="96" height="96" style="border-radius: 50%;" alt="김홍근" />
      </a>
      <br />
      <strong>김홍근</strong>
      <br />
      <sub>PM / Backend Lead</sub>
      <br />
      <a href="https://github.com/khg9859">@khg9859</a>
    </td>
    <td align="center" width="180">
      <a href="https://github.com/nYeonG4001">
        <img src="https://github.com/nYeonG4001.png" width="96" height="96" style="border-radius: 50%;" alt="박하영" />
      </a>
      <br />
      <strong>박하영</strong>
      <br />
      <sub>Backend</sub>
      <br />
      <a href="https://github.com/nYeonG4001">@nYeonG4001</a>
    </td>
    <td align="center" width="180">
      <a href="https://github.com/suheon98">
        <img src="https://github.com/suheon98.png" width="96" height="96" style="border-radius: 50%;" alt="조수헌" />
      </a>
      <br />
      <strong>조수헌</strong>
      <br />
      <sub>AX</sub>
      <br />
      <a href="https://github.com/suheon98">@suheon98</a>
    </td>
    <td align="center" width="180">
      <a href="https://github.com/uiuuoq">
        <img src="https://github.com/uiuuoq.png" width="96" height="96" style="border-radius: 50%;" alt="홍보민" />
      </a>
      <br />
      <strong>홍보민</strong>
      <br />
      <sub>Frontend</sub>
      <br />
      <a href="https://github.com/uiuuoq">@uiuuoq</a>
    </td>
  </tr>
</table>
