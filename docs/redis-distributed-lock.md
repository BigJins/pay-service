# Redis 분산 락 — 결제 중복 처리 방지

## 왜 필요한가

결제 확인 API는 **멱등성이 없는** 외부 호출(Toss API)을 포함한다.
동일한 `paymentKey`로 동시 요청이 들어오면:

```
Thread A: existsByPaymentKey → false
Thread B: existsByPaymentKey → false     ← A가 아직 저장 전
Thread A: Toss confirm → DONE
Thread B: Toss confirm → DONE            ← 중복 승인 호출
Thread A: save(APPROVED) → 성공
Thread B: save(APPROVED) → DB unique constraint 위반 → 500 에러
```

Before: DB에는 1건만 저장되지만 19개 요청이 **500 에러**를 받는다.
After: 락을 획득한 1개만 처리, 나머지 19개는 **409 Conflict** (정상적 거절).

---

## Redis SET NX EX 원리

```
SET payment:lock:{paymentKey} "1" NX EX 30
```

- `NX` (Not eXists): 키가 없을 때만 세팅 → **원자적** 락 획득
- `EX 30`: 30초 TTL → 서버 크래시 시 데드락 방지
- 반환값: 1 = 락 획득 성공, nil = 이미 잠김

```
Redis: GET payment:lock:pk_abc → (nil)

Thread A: SET payment:lock:pk_abc "1" NX EX 30 → OK  (락 획득)
Thread B: SET payment:lock:pk_abc "1" NX EX 30 → nil (락 실패 → 409)
Thread C: SET payment:lock:pk_abc "1" NX EX 30 → nil (락 실패 → 409)

Thread A 처리 완료 → DEL payment:lock:pk_abc

Redis: GET payment:lock:pk_abc → (nil)  (락 해제)
```

---

## 구현

### build.gradle.kts

```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-redis")
```

Spring Boot가 `StringRedisTemplate` Bean을 자동으로 등록한다.

### application-local.yml

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

### compose.yaml (Redis 서비스 추가)

```yaml
redis:
  image: 'redis:7-alpine'
  ports:
    - '6379:6379'
```

### Port 인터페이스 (application/required)

```java
public interface PaymentLockPort {
    boolean tryLock(String paymentKey);
    void unlock(String paymentKey);
}
```

application 레이어는 Redis를 직접 알지 못한다. 인터페이스만 의존.

### Redis Adapter (adapter/redis)

```java
@Component
@RequiredArgsConstructor
public class RedisPaymentLockAdapter implements PaymentLockPort {

    private static final String LOCK_PREFIX = "payment:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean tryLock(String paymentKey) {
        // setIfAbsent = Redis SET NX EX (원자적)
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_PREFIX + paymentKey, "1", LOCK_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void unlock(String paymentKey) {
        redisTemplate.delete(LOCK_PREFIX + paymentKey);
    }
}
```

**포인트:**
- `setIfAbsent()` = `SET NX EX` — 내부적으로 단일 Redis 커맨드 → 원자성 보장
- `Boolean.TRUE.equals()` — null 방어 (연결 실패 시 null 반환 가능)
- `LOCK_TTL = 30s` — Toss API 최대 대기(10s) + 재시도(2회 × 500ms backoff) + 여유

### Service (락 적용)

```java
@Override
public PaymentLedger approve(PaymentReadyRequest request, String paymentKey) {

    // 1. 분산 락 획득 시도
    if (!paymentLockPort.tryLock(paymentKey)) {
        throw new PaymentConflictException(paymentKey);  // → 409
    }

    try {
        // 2. 멱등성 체크 (재시도 케이스)
        if (paymentRepository.existsByPaymentKeyAndEventType(paymentKey, APPROVED)) {
            return paymentRepository.findByPaymentKeyAndEventType(paymentKey, APPROVED)
                    .orElseThrow(...);
        }

        // 3. Toss 호출 (락 보호 구간)
        TossConfirmResult result = tossPayments.confirm(request, paymentKey);
        return result.approved()
                ? approveAndPublish(request, paymentKey, result)
                : failAndPublish(request, result);

    } finally {
        // 4. 처리 완료 즉시 락 해제 (TTL 기다리지 않음)
        paymentLockPort.unlock(paymentKey);
    }
}
```

**finally 블록이 중요한 이유:**
예외가 발생해도 락이 반드시 해제된다. finally 없으면 예외 시 30초간 다음 요청 전부 차단.

### 예외 + 컨트롤러 어드바이스

```java
// application/PaymentConflictException.java
public class PaymentConflictException extends RuntimeException {
    public PaymentConflictException(String paymentKey) {
        super("이미 처리 중인 결제입니다: " + paymentKey);
    }
}

// adapter/webapi/GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(PaymentConflictException.class)
    public ResponseEntity<String> handlePaymentConflict(PaymentConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
}
```

---

## Before / After 비교

### Before (락 없음)

```
k6 결과: 200 × 1건, 500 × 19건
DB: APPROVED 1건 (unique constraint가 중복 저장은 막음)
문제: 19개 스레드가 500 에러 → 사용자는 결제 실패로 오인
```

### After (Redis 락)

```
k6 결과: 200 × 1건, 409 × 19건
DB: APPROVED 1건
개선: 500 → 409로 전환 (서버 에러 → 명시적 중복 거절)
```

---

## 락 TTL 설계 기준

```
Toss API responseTimeout  = 10s
Retry 최대 대기           = 2회 × 500ms backoff ≈ 1.5s
block() 타임아웃          = 15s
--------------------------------------
총 최대 소요              ≈ 16.5s
락 TTL                   = 30s  (충분한 여유 포함)
```

TTL이 너무 짧으면: Toss 처리 완료 전에 락이 풀려 중복 진입 가능
TTL이 너무 길면: 서버 크래시 후 복구까지 최대 TTL만큼 대기

---

## 한계 및 발전 방향

| 현재 구현 | 한계 | 발전 방향 |
|-----------|------|-----------|
| Redis 단일 노드 | Redis 장애 시 락 불가 | Redlock 알고리즘 (3개 이상 노드) |
| 단순 SET NX | 락 소유자 검증 없음 | 락 값에 UUID 저장, DEL 전 소유자 확인 |
| tryLock (즉시 실패) | 재시도 없음 | Retry + backoff (Redisson RLock) |
| Spring Data Redis | 직접 구현 | Redisson `@RLock` AOP |

현재 구현은 **단일 Redis 노드 + tryLock** 방식으로 충분히 실용적이다.
운영 Redis 클러스터를 사용하거나 재시도 락이 필요할 때 Redisson 도입을 고려한다.