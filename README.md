# devpick-backend

> Trace, 개발자 성장 플랫폼 Spring Boot API 서버. 2026 캡스톤 백엔드 저장소입니다.
> 전체 프로젝트 소개는 [Devpick-Org](https://github.com/Devpick-Org) 에서 확인할 수 있습니다.

---

## 기술 스택

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
| ORM | JPA, Hibernate, QueryDSL |
| DB | PostgreSQL 16 on AWS RDS |
| 캐시 | Redis 7 on AWS ElastiCache |
| 비정형 DB | DynamoDB, AI 요약과 퀴즈, 리포트 인사이트 저장 |
| 인프라 | AWS EC2, Docker, Nginx |
| CI/CD | GitHub Actions, SonarCloud |
| 인증 | JWT Access Token과 Refresh Token, OAuth2 GitHub Google |
| 테스트 | JUnit 5, Mockito, JaCoCo |
| API 문서 | Swagger, OpenAPI |

---

## 시스템 구조

```
브라우저
  └─ Nginx, TLS
       ├─ Next.js 프론트엔드 port 3000
       └─ Spring Boot API 서버 port 8080
              ├─ PostgreSQL on AWS RDS port 5432
              ├─ Redis on AWS ElastiCache port 6379
              ├─ DynamoDB on AWS
              └─ FastAPI AI 서버 port 8000
```

---

## 프로젝트 구조

```
src/main/java/com/devpick
├── domain
│   ├── user         # 회원가입, 로그인, 소셜인증, 프로필
│   ├── content      # 콘텐츠 피드, 스크랩, AI 요약, AI 퀴즈, 맞춤 추천
│   ├── community    # 게시글, 답변, AI 질문 개선, AI 답변, 첨부파일
│   ├── report       # 주간 리포트, 학습 히스토리
│   ├── point        # 포인트 적립, 배지
│   ├── job          # 채용 공고, 북마크, 모의면접
│   ├── resume       # 이력서 관리
│   ├── subscription # 구독 플랜, 토스페이먼츠 결제
│   └── trend        # 트렌드 분석
└── global
    ├── common       # 예외 처리, 공통 응답
    ├── config       # Security, CORS, Swagger, S3, DynamoDB, Async, Jackson, WebClient
    ├── controller   # 헬스 체크
    ├── entity       # 공통 Base 엔티티
    ├── security     # JWT 필터, 토큰 프로바이더
    ├── storage      # 파일 스토리지
    └── util         # 유틸리티
```

---

## 주요 기능

| 도메인 | 기능 |
|--------|------|
| 인증 | 이메일 회원가입과 로그인, GitHub Google OAuth2, JWT 갱신 |
| 프로필 | 내 프로필 조회와 수정, 프로필 이미지 업로드, 타인 공개 프로필 조회, 회원 탈퇴 |
| 콘텐츠 | 관심 태그 기반 개인화 피드, 스크랩, 좋아요, 스크랩 목록 조회, 검색, 콘텐츠와 유튜브와 서적까지 다루는 맞춤 추천 |
| AI | 레벨별 AI 요약, AI 퀴즈와 퀴즈 히스토리, AI 질문 개선, AI 답변 생성 |
| 커뮤니티 | 질문 게시글, 답변 채택, 댓글, 첨부파일 업로드, 유사 질문 조회, AI 답변 생성 |
| 리포트 | 주간 학습 리포트 생성과 공유, 학습 히스토리 |
| 포인트 | 학습 행동별 포인트 적립, 배지 시스템 |
| 채용 | 채용 공고 수집과 매칭, 북마크와 북마크 목록 조회, 면접 Q&A, 모의면접, 부족 역량 보완 추천 |
| 이력서 | 이력서 관리, 문서 파싱, AI 보강 |
| 트렌드 | 에코시스템 트렌드에서 부트캠프와 개발행사, 개발동아리 정리, 트렌딩 키워드, 주간 상위 콘텐츠 집계 |
| 구독 | Free, Pro, Max 플랜, 토스페이먼츠 빌링키 결제와 해지, 환불, 플랜별 기능 횟수 제한 |
| 모니터링 | Sentry로 에러와 트레이싱, Prometheus와 Grafana로 JVM과 HTTP p95, HikariCP 관측, Loki로 컨테이너 로그 수집, k6 부하 테스트 |

---

## Getting Started

### 사전 요구사항

- Java 21
- Docker, Docker Compose
- `.env` 파일, `.env.example` 참고

### 로컬 실행, PostgreSQL과 Redis와 앱

```bash
git clone https://github.com/Devpick-Org/devpick-backend.git
cd devpick-backend
cp .env.example .env   # 환경변수 설정
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
```

### DB와 캐시만 실행, 앱은 IDE에서 직접

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d postgres redis
./gradlew bootRun
```

### 빌드와 테스트

```bash
./gradlew build --no-daemon        # 빌드와 전체 테스트
./gradlew test --no-daemon         # 테스트만
./gradlew jacocoTestReport         # 커버리지 리포트 생성
```

---

## API 문서

로컬 실행 후 `http://localhost:8080/swagger-ui/index.html` 에서 확인할 수 있습니다.

---

## 성능 개선, k6 부하 테스트

운영 EC2에서 공개 GET API에 **k6 stress 150 VU**를 걸어 병목을 찾고, **Redis 캐시와 쿼리, DB 커넥션 풀**을 튜닝해 응답 지연을 줄였습니다.

### 측정 환경

- 부하 시나리오는 [`loadtest/k6/stress.js`](loadtest/k6/stress.js) 입니다. 25 VU에서 시작해 50, 100, 150 VU까지 단계로 올립니다.
- 대상 API는 `GET /health`, `/posts`, `/contents`, `/trends/keywords`, `/trends/ecosystem` 입니다.
- 결과는 k6에서 InfluxDB로 보내고 Grafana 대시보드에서 봅니다.
- 동시에 Prometheus와 Grafana `Devpick Backend Overview` 대시보드로 JVM, p95, HikariCP를 관찰합니다.

### Before, 튜닝 전 150 VU stress

![k6 before](docs/perf/k6-before.png)

| 지표 | 값 |
|------|------|
| p95 peak | 약 7s, Stat 4.28s |
| mean | 506 ms |
| max | 32.29 s |
| HikariCP pending | 약 82 |

### After, 튜닝 후 동일 150 VU stress

![k6 after](docs/perf/k6-after.png)

| 지표 | 값 |
|------|------|
| **p95** | **189 ms** |
| mean | 70 ms |
| max | 2.08 s |
| HikariCP pending | 약 24 |

### 개선 요약

| 지표 | Before | After | 개선 |
|------|--------|-------|------|
| p95 | 약 7 s | **189 ms** | **약 97% 감소** |
| mean | 506 ms | 70 ms | **약 86% 감소** |
| max | 32.29 s | 2.08 s | **약 94% 감소** |
| HikariCP pending | 82 | 24 | **약 71% 감소** |

### 어떻게 줄였나

- **공개 목록 API Redis 캐시**: `PostService` 와 `ContentService` 목록 응답을 TTL 30초로 캐싱해 동일한 page와 query 요청의 DB 왕복을 줄였습니다.
- **트렌드 생태계 API 스냅샷 캐시**: `EcosystemTrendService` 가 6시간 스케줄로 외부 3개 소스를 수집해 Redis와 in-memory snapshot에 저장하고, API는 캐시 read 위주로 동작합니다. 캐시가 비어 있으면 백그라운드에서 refresh 합니다.
- **DB 커넥션 풀 보호**: hot path 쿼리와 호출 수를 줄여 부하 시 HikariCP pending을 82에서 24 수준으로 완화했습니다.

> 부하 테스트 실행 방법과 시나리오 상세는 [`loadtest/README.md`](loadtest/README.md) 에서 확인할 수 있습니다.

---

## CI/CD

| Job | 트리거 | 설명 |
|-----|--------|------|
| Build & Test | PR -> developV2 | Gradle 빌드와 JUnit 테스트 |
| SonarCloud | PR -> developV2 | 커버리지와 코드 품질 분석, non-blocking |
| Auto Merge | `auto/` 브랜치 PR | CI 통과 시 developV2 자동 squash 머지 |
| Deploy | push -> developV2 | EC2 SSH 자동 배포 |
| Backend CD | 수동 트리거 | GHCR Docker 이미지 빌드 |

---

## 브랜치 전략

| 브랜치 | 용도 |
|--------|------|
| `main` | 배포용 |
| `develop` | MVP |
| `developV2` | MVP 이후 통합 브랜치 |
| `feature/DP-{번호}-{기능명}` | 기능 개발 |
| `fix/DP-{번호}-{설명}` | 버그 수정 |
| `auto/feature/DP-{번호}-{기능명}` | 기능 개발, CI 통과 시 자동 머지 |
| `auto/fix/DP-{번호}-{설명}` | 버그 수정, CI 통과 시 자동 머지 |

---

## 팀

<table>
  <tr>
    <td align="center" width="180">
      <a href="https://github.com/khg9859">
        <img src="https://github.com/khg9859.png" width="96" height="96" style="border-radius: 50%;" alt="김홍근" />
      </a>
      <br />
      <strong>김홍근</strong>
      <br />
      <sub>PM, Backend Lead</sub>
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
  </tr>
</table>
