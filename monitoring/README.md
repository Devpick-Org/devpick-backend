# Prometheus + Grafana (EC2)

## 한 줄 요약

1. 백엔드가 `/actuator/prometheus`로 메트릭을 노출한다.
2. 같은 EC2의 Prometheus가 `app:8080`을 내부 Docker 네트워크로 수집한다.
3. Grafana만 nginx + Basic Auth + HTTPS로 공개한다.

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
- 외부 접속: nginx가 `https://grafana.3-39-96-126.sslip.io`로 프록시

Prometheus target은 백엔드 Compose 네트워크의 `app:8080`입니다.

```bash
docker compose ps
```

## 3) Grafana 설정

Prometheus datasource는 `grafana/provisioning`으로 자동 등록됩니다.

1. `https://grafana.3-39-96-126.sslip.io` 접속
2. nginx Basic Auth 통과
3. Grafana 로그인 (`.env`의 `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`)
4. **Dashboards → New → Import** 에서 ID `4701` (JVM Micrometer) 또는 `12900` (Spring Boot) import

## 4) 운영(EC2) 주의

`/actuator/prometheus`는 **인증 없이** 메트릭을 노출합니다.

- Prometheus/Grafana 포트는 127.0.0.1에만 바인딩합니다.
- 보안그룹에서 9090/3000/3001은 열지 않습니다.
- Grafana 공개 URL은 nginx Basic Auth와 HTTPS로 보호합니다.
