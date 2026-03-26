package allmart.payservice.application;

import allmart.payservice.application.required.PaymentRepository;
import allmart.payservice.domain.PaymentEventType;
import allmart.payservice.domain.PaymentLedger;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
@Log4j2
public class PaymentWebhookService {

    private final PaymentRepository paymentRepository;

    public void handle(String tossOrderId, String paymentKey, long amount, String tossStatus, String rawJson) {

        PaymentEventType eventType = mapToEventType(tossStatus);

        log.info("WEBHOOK parsed status={}, orderId={}, key={}", tossStatus, tossOrderId, paymentKey);
        // 중복 방지: 같은 paymentKey+eventType 있으면 스킵
        if (paymentKey != null && paymentRepository.existsByPaymentKeyAndEventType(paymentKey, eventType)) {
            return;
        }
        if (paymentRepository.existsByTossOrderIdAndEventType(tossOrderId, eventType)) {
            log.info("SKIP: already exists tossOrderId={}, eventType={}", tossOrderId, eventType);
            return;
        }

        PaymentLedger ledger = PaymentLedger.fromWebhook(
                tossOrderId,
                paymentKey,
                amount,
                tossStatus,
                eventType,
                rawJson
        );

        paymentRepository.save(ledger);

    }

    private PaymentEventType mapToEventType(String tossStatus) {
        if ("DONE".equalsIgnoreCase(tossStatus)) return PaymentEventType.APPROVED;
        return PaymentEventType.FAILED;
    }
}
