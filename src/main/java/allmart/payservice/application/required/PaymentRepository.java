package allmart.payservice.application.required;

import allmart.payservice.domain.PaymentEventType;
import allmart.payservice.domain.PaymentLedger;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface PaymentRepository extends Repository<PaymentLedger, Long> {
    PaymentLedger save(PaymentLedger paymentLedger);

    boolean existsByPaymentKeyAndEventType(String paymentKey, PaymentEventType eventType);

    boolean existsByTossOrderIdAndEventType(String tossOrderId, PaymentEventType eventType);

    Optional<PaymentLedger> findTopByTossOrderIdOrderByCreatedAtDesc(String tossOrderId);

    Optional<PaymentLedger> findByPaymentKeyAndEventType(String paymentKey, PaymentEventType eventType);
}
