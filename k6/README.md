# k6 부하 테스트

## 주의: localhost 대신 127.0.0.1 사용

Windows에서 `localhost`가 IPv6(`::1`)로 해석될 경우 k6 연결 실패.
WSL에서 실행하면 `data_sent: 0 B` — **반드시 PowerShell에서 실행**할 것.
스크립트는 모두 `127.0.0.1`로 고정되어 있음.

---

## 전체 테스트 실행 순서

### 1단계 — 인프라 기동

```powershell
cd C:\newpractice\pay-service

# MySQL, Redis, Kafka, Debezium 기동
docker compose up -d
```

Debezium 커넥터는 컨테이너 기동 후 별도 등록 필요 (최초 1회).

### 2단계 — 서비스 기동

각 서비스를 별도 터미널에서 실행한다.

```powershell
# 터미널 1 — order-service (port 8081)
cd C:\newpractice\order-service
./gradlew bootRun --args='--spring.profiles.active=local'

# 터미널 2 — pay-service (port 8080)
cd C:\newpractice\pay-service
./gradlew bootRun --args='--spring.profiles.active=local'

# 터미널 3 — apigateway-service (port 8000, rate-limit.js 테스트 시에만 필요)
cd C:\newpractice\apigateway-service
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 3단계 — 연결 확인

```powershell
curl http://127.0.0.1:8081/actuator/health   # order-service
curl http://127.0.0.1:8080/actuator/health   # pay-service
curl http://127.0.0.1:8000/actuator/health   # apigateway-service
```

---

## 테스트 목록 및 실행 순서

> 테스트 간 DB 상태가 누적되므로 아래 순서대로 실행을 권장한다.

### [1] race-condition.js — Redis 분산 락 검증

**목적**: 동일 paymentKey 동시 요청 시 race condition이 없는지 확인

**필요 서비스**: pay-service, Redis

```powershell
# DB 초기화 (이전 실행 데이터가 남아있으면 테스트 무효)
# pay-service MySQL에서 실행:
# DELETE FROM payment_ledger WHERE toss_order_id = 'RACE_TEST_ORDER_1';

k6 run k6\race-condition.js
```

**예상 결과 (Redis 락 적용 후)**:

| 항목 | 값 |
|------|----|
| checks 통과 | 40/40 (100%) |
| 200 응답 | 1건 (락 획득, 결제 처리) |
| 409 응답 | 19건 (락 획득 실패, 즉시 거절) |
| 500 응답 | 0건 ✅ |

**DB 확인**:
```sql
SELECT COUNT(*), event_type
FROM payment_ledger
WHERE toss_order_id = 'RACE_TEST_ORDER_1'
GROUP BY event_type;
-- 결과: APPROVED 1건만 존재해야 함
```

> **참고**: 이 테스트의 tossOrderId는 order-service에 주문이 없는 가짜 ID다.
> order-service 로그에 `WARN: Kafka 메시지 무시 — 존재하지 않는 tossOrderId` 가 찍히는 건 정상.

---

### [2] baseline.js — pay-service 처리량 측정

**목적**: 워밍업 → 일반 부하 → 피크 부하 단계별 처리량 측정

**필요 서비스**: pay-service, Redis, MySQL

```powershell
k6 run k6\baseline.js
```

**확인할 지표 (Grafana)**:
- `http_server_requests_seconds` P95, P99
- `hikaricp_connections_active` (DB 커넥션 풀)
- JVM 스레드 수

---

### [3] order-load.js — order-service 처리량 측정

**목적**: 주문 생성 API 부하 테스트

**필요 서비스**: order-service, MySQL

```powershell
k6 run k6\order-load.js
```

---

### [4] rate-limit.js — Gateway Rate Limiting 검증

**목적**: Gateway가 초과 요청을 429로 차단하고 downstream을 보호하는지 확인

**필요 서비스**: apigateway-service (port 8000), pay-service, Redis

```powershell
k6 run k6\rate-limit.js
```

**예상 결과**:

| 항목 | 값 |
|------|----|
| 500 응답 | 0건 ✅ |
| 429 응답 | 다수 (Rate Limit 초과 차단) |
| 200/409 응답 | 소수 (버킷 내 처리) |

**핵심 비교**: rate-limit.js를 Gateway(8000) vs pay-service 직접(8080) 으로 각각 실행하면 downstream 도달 요청 수 차이를 Prometheus에서 확인할 수 있다.

```powershell
# Gateway 경유 (rate-limit.js 기본값 — 8000)
k6 run k6\rate-limit.js

# pay-service 직접 (BASE_URL을 8080으로 바꿔서 실행)
# → Prometheus에서 pay-service RPS 비교
```

---

## Kafka 관련 주의사항

k6 테스트는 pay-service에 직접 결제 요청을 보낸다.
Debezium이 기동 중이라면 결제 결과가 Kafka를 통해 order-service로 전달된다.

order-service에 존재하지 않는 tossOrderId는 **WARN 로그로 기록되고 무시**된다 (정상 동작).
```
WARN: Kafka 메시지 무시 — 존재하지 않는 tossOrderId: RACE_TEST_ORDER_1
```

재시도 루프나 에러로 이어지지 않으므로 무시해도 된다.
