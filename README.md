# pay-service

**allmart** 이커머스 플랫폼의 결제 도메인 마이크로서비스입니다.
Toss Payments API 연동, 분산 락을 통한 중복 결제 방지, Outbox 패턴으로 신뢰성 있는 이벤트 발행을 담당합니다.

## 관련 서비스

| 서비스 | 역할 | GitHub |
|--------|------|--------|
| **order-service** | 주문 생성 / 상태 관리 | [BigJins/order-service](https://github.com/BigJins/order-service) |
| **pay-service** | 결제 승인 / Toss Payments 연동 | 현재 레포 |
| **apigateway-service** | Rate Limiting / 라우팅 | [BigJins/apigateway-service](https://github.com/BigJins/apigateway-service) |
| **config-server** | 런타임 설정 중앙 관리 | [BigJins/config-server](https://github.com/BigJins/config-server) |
| **allmart-configs** | Config Server 설정 파일 저장소 | [BigJins/allmart-configs](https://github.com/BigJins/allmart-configs) |

## 기술 스택

- **Java 21** + Spring Boot 4.0.2
- **Hexagonal Architecture** (adapter / application / domain)
- **Spring Data JPA** + MySQL 8
- **Apache Kafka** — 결제 결과 이벤트 발행 (`payment.result.v1`)
- **WebClient (WebFlux)** — Toss API 비동기 호출, 타임아웃 + 재시도
- **Redis** — 분산 락 (`SET NX EX`)
- **Java 21 Virtual Threads**
- **Spring Cloud Config Client** — Toss 재시도/타임아웃 런타임 변경
- **Outbox Pattern** — DB 저장 + 이벤트 발행 트랜잭션 일관성
- **Debezium CDC** — Outbox 이벤트 → Kafka 자동 릴레이

## 주요 구현 포인트

### Redis 분산 락 — 중복 결제 방지
동일 `paymentKey`로 동시 요청 시 `SET NX EX` 원자적 락으로 진입 차단.

```
Before: 동시 요청 20건 → 500 에러 19건 (DataIntegrityViolationException)
After:  동시 요청 20건 → 409 19건 (락 획득 실패, 즉시 거절)
```

### WebClient — Toss API 호출
- 연결 타임아웃 3s / 응답 타임아웃 10s
- 네트워크 오류 시 최대 2회 재시도 (500ms 백오프)
- 설정값은 Config Server에서 런타임 변경 가능

### Outbox Pattern
결제 승인/실패 시 `payment_ledger` 저장 + `outbox_events` 저장을 **단일 트랜잭션**으로 처리.
Kafka 장애 시에도 이벤트 유실 없음. Debezium CDC가 Outbox → Kafka 릴레이 담당.

## API

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/payments/confirm` | 결제 승인 요청 |
| GET | `/api/payments/{tossOrderId}` | 결제 상태 조회 |
| POST | `/toss/webhook` | Toss 웹훅 수신 |

## k6 부하 테스트 결과

### Redis 분산 락 (race-condition.js)

| 항목 | 락 없음 | Redis 락 적용 |
|------|---------|--------------|
| 성공 (200) | 1건 | 1건 |
| 서버 에러 (500) | **19건** | **0건** ✅ |
| 정상 거절 (409) | 0건 | 19건 |

## 실행

```bash
# 인프라 시작 (Kafka, MySQL, Redis, Debezium)
docker compose up -d

# 서비스 실행
./gradlew bootRun --args='--spring.profiles.active=local'
```

> 로컬 실행 전 `.env`, `application-local.yml` 파일 생성 필요 (`.gitignore`에 포함된 파일)