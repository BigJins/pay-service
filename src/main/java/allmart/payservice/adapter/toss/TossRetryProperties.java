package allmart.payservice.adapter.toss;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Toss API 재시도/타임아웃 설정.
 * Config Server의 pay-service.yml에서 주입되며,
 * /actuator/refresh 호출 시 재시작 없이 즉시 반영된다.
 *
 * 관리 위치: C:/newpractice/configs/pay-service.yml
 */
@Getter
@Setter
@Component
@ConfigurationProperties("toss")
public class TossRetryProperties {

    private Retry retry = new Retry();

    /** Toss API 전체 호출 타임아웃 (초) */
    private int timeoutSeconds = 15;

    @Getter
    @Setter
    public static class Retry {
        /** 네트워크 오류 시 재시도 횟수 (0 = 재시도 안 함) */
        private int maxAttempts = 2;
        /** 재시도 사이 백오프 대기 시간 (ms) */
        private long backoffMs = 500;
    }
}
