# Prometheus + Grafana (EC2)

## 한 줄 요약

1. 백엔드가 `/actuator/prometheus`로 메트릭을 노출한다.
2. 같은 EC2의 Prometheus가 `app:8080`을 내부 Docker 네트워크로 수집한다.
3. Promtail이 Docker 컨테이너 로그를 Loki로 보낸다.
4. Grafana만 기존 nginx HTTPS 도메인의 `/grafana/` 경로에서 Basic Auth로 공개한다.

## 1) 백엔드

- `build.gradle`: `micrometer-registry-prometheus`
- `application.yml`: `management.endpoints.web.exposure.include`에 `prometheus`
- `SecurityConfig`: `GET /actuator/prometheus` 허용 (스크레이프용)

확인:

```bash
curl -s http://127.0.0.1:8080/actuator/prometheus | head
```

## 2) 모니터링 스택 실행

EC2의 `/home/ubuntu/devpick-backend/monitoring/.env`에 값을 넣은 뒤 실행:

```bash
cd monitoring
docker compose up -d
```

- Prometheus: `127.0.0.1:9090` 에만 바인딩
- Grafana: `127.0.0.1:3001` 에만 바인딩
- Loki: `127.0.0.1:3100` 에만 바인딩
- InfluxDB(k6 결과 저장): `127.0.0.1:8086` 에만 바인딩
- 외부 접속: nginx가 `https://3-39-96-126.sslip.io/grafana/`로 프록시

Prometheus target은 백엔드 Compose 네트워크의 `app:8080`입니다.

```bash
docker compose ps
```

## 3) Grafana 설정

Prometheus datasource는 `grafana/provisioning`으로 자동 등록됩니다.
Loki datasource도 `grafana/provisioning`으로 자동 등록됩니다.

1. `https://3-39-96-126.sslip.io/grafana/` 접속
2. nginx Basic Auth 통과
3. Grafana 로그인 (`.env`의 `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`)
4. **Dashboards → New → Import** 에서 ID `4701` (JVM Micrometer) 또는 `12900` (Spring Boot) import
5. **Explore → Loki** 에서 로그 조회
   - 백엔드: `{container="devpick-backend-app-1"}`
   - AI: `{container="devpick-ai-app-1"}`
   - Grafana: `{container="devpick-grafana"}`
   - 에러만 보기: `{container="devpick-backend-app-1"} |= "ERROR"`

## 4) Sentry

Sentry는 에러 추적과 분산 트레이싱을 맡는다. DSN을 넣지 않으면 비활성화된다.

- Frontend(Vercel): `NEXT_PUBLIC_SENTRY_DSN`, `NEXT_PUBLIC_SENTRY_ENVIRONMENT`, `NEXT_PUBLIC_SENTRY_TRACES_SAMPLE_RATE`
- Backend(EC2 `.env`): `SENTRY_DSN`, `SENTRY_ENVIRONMENT`, `SENTRY_TRACES_SAMPLE_RATE`
- AI(EC2 `~/devpick-ai/.env`): `SENTRY_DSN`, `SENTRY_ENVIRONMENT`, `SENTRY_TRACES_SAMPLE_RATE`

대시보드 확인:

1. Sentry 프로젝트 3개를 만든다: `trace-frontend`, `trace-backend`, `trace-ai`
2. 각 DSN을 환경변수에 넣고 배포한다.
3. Sentry → **Issues**: 운영 에러 그룹 확인
4. Sentry → **Performance/Traces**: 느린 transaction, 서비스 간 trace 확인
5. Sentry → **Releases**: `SENTRY_AUTH_TOKEN`, `SENTRY_ORG`, `SENTRY_PROJECT`를 설정하면 프론트 sourcemap 업로드와 릴리스 추적 가능

## 5) 운영(EC2) 주의

`/actuator/prometheus`는 **인증 없이** 메트릭을 노출합니다.

- Prometheus/Grafana 포트는 127.0.0.1에만 바인딩합니다.
- 보안그룹에서 9090/3000/3001/3100/8086은 열지 않습니다.
- Grafana 공개 URL은 nginx Basic Auth와 HTTPS로 보호합니다.
