package allmart.payservice.adapter.toss;

import allmart.payservice.application.required.TossConfirmResult;
import allmart.payservice.application.required.TossPayments;
import allmart.payservice.domain.PaymentReadyRequest;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("local")
@Component
@Primary
public class TossPaymentsStub implements TossPayments {

    // 실제 Toss API 평균 응답시간 수준의 지연 (150ms)
    // RestTemplate vs WebClient 스레드 점유 차이를 측정하기 위한 의도적 설정
    private static final long SIMULATED_LATENCY_MS = 150;

    @Override
    public TossConfirmResult confirm(PaymentReadyRequest request, String paymentKey) {
        try {
            Thread.sleep(SIMULATED_LATENCY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return TossConfirmResult.approved("DONE", "{\"stub\":true}");
    }

}
