package allmart.payservice.adapter.redis;

import allmart.payservice.application.required.PaymentLockPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisPaymentLockAdapter implements PaymentLockPort {

    // 락 키: "payment:lock:{paymentKey}"
    private static final String LOCK_PREFIX = "payment:lock:";

    // TTL: Toss API 타임아웃(10s) + 재시도(2회 * 500ms) + 여유 = 30s
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean tryLock(String paymentKey) {
        // SET NX EX — 키 없을 때만 세팅 (원자적)
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_PREFIX + paymentKey, "1", LOCK_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void unlock(String paymentKey) {
        redisTemplate.delete(LOCK_PREFIX + paymentKey);
    }
}