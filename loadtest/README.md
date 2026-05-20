# k6 Load Test

## 목적

운영 EC2에서 공개 GET API에 부하를 주고, k6 결과는 InfluxDB에 저장한 뒤 Grafana 대시보드에서 확인합니다.

동시에 기존 Prometheus 기반 `Devpick Backend Overview` 대시보드에서 백엔드의 RPS, p95/p99, JVM, Hikari 상태를 같이 봅니다.

## 구성

- `k6/smoke.js`: 1 VU, 30초. 설정과 저장 경로가 맞는지 확인합니다.
- `k6/load.js`: 10 VU 유지 후 30 VU 유지. 일반 부하 관찰용입니다.
- `k6/stress.js`: 25 → 50 → 100 → 150 VU. 한계 지점을 찾는 용도입니다.
- `k6/lib/scenarios.js`: 공통 공개 GET 시나리오입니다.

1회 iteration은 다음 요청을 순서대로 보냅니다.

- `GET /health`
- `GET /posts?page=0&size=6`
- `GET /contents?page=0&size=6`
- `GET /trends/keywords`
- `GET /trends/ecosystem?limit=80&offset=0`

## 실행 위치

EC2 `/home/ubuntu/devpick-backend`에서 실행합니다.

같은 EC2에서 k6를 돌리면 k6와 백엔드가 CPU를 나눠 쓰므로, 절대 성능값보다는 변경 전후 비교와 병목 탐지에 더 적합합니다.

## 사전 준비

모니터링 스택을 켭니다.

```bash
cd /home/ubuntu/devpick-backend/monitoring
docker compose up -d
curl -i http://127.0.0.1:8086/ping
```

InfluxDB가 정상이라면 `204 No Content`가 나옵니다.

## Smoke Test

가장 먼저 이 테스트부터 실행합니다.

```bash
cd /home/ubuntu/devpick-backend
docker run --rm -i \
  --network devpick-backend_default \
  -v "$PWD/loadtest/k6":/scripts \
  grafana/k6:0.49.0 run \
  -e BASE_URL=http://app:8080 \
  --out influxdb=http://devpick-influxdb:8086/k6 \
  /scripts/smoke.js
```

## Load Test

```bash
cd /home/ubuntu/devpick-backend
docker run --rm -i \
  --network devpick-backend_default \
  -v "$PWD/loadtest/k6":/scripts \
  grafana/k6:0.49.0 run \
  -e BASE_URL=http://app:8080 \
  --out influxdb=http://devpick-influxdb:8086/k6 \
  /scripts/load.js
```

## Stress Test

운영 트래픽이 적은 시간대에만 실행하세요.

```bash
cd /home/ubuntu/devpick-backend
docker run --rm -i \
  --network devpick-backend_default \
  -v "$PWD/loadtest/k6":/scripts \
  grafana/k6:0.49.0 run \
  -e BASE_URL=http://app:8080 \
  --out influxdb=http://devpick-influxdb:8086/k6 \
  /scripts/stress.js
```

## 외부 도메인 경유 테스트

nginx, TLS, 네트워크까지 포함해서 보고 싶으면 `BASE_URL`만 바꿉니다.

```bash
-e BASE_URL=https://3-39-96-126.sslip.io
```

## Grafana에서 보기

1. `https://3-39-96-126.sslip.io/grafana/` 접속
2. Dashboards → New → Import
3. Dashboard ID `2587` 또는 `18105` 입력
4. datasource로 `InfluxDB` 선택

같은 시간대에 `Devpick Backend Overview`도 함께 보면 서버 리소스와 API latency를 같이 해석할 수 있습니다.

## VUser 계산 감 잡기

간단 공식:

```text
VUser = 목표 RPS x 시나리오 1회 소요 시간 / 시나리오 요청 수
```

예를 들어 목표 50 RPS, 시나리오 1회가 1초, 요청 5개라면:

```text
50 x 1 / 5 = 10 VU
```

처음에는 `smoke.js`, 이후 `load.js`, 마지막으로 `stress.js` 순서로 올립니다.

## 결과 해석 기준

- `http_req_failed`: 실패율입니다. 1%를 넘기면 원인을 봅니다.
- `http_req_duration p(95)`: 대부분의 사용자가 체감하는 느림입니다.
- `http_req_duration p(99)`: 가끔 튀는 느린 요청입니다.
- Grafana `Devpick Backend Overview`의 JVM/Hikari 패널이 같이 튀면 서버 내부 병목일 가능성이 큽니다.
