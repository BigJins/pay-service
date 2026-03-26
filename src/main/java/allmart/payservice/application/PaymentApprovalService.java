package allmart.payservice.application;

import allmart.payservice.application.provided.PaymentApproval;
import allmart.payservice.application.required.PaymentLockPort;
import allmart.payservice.application.required.PaymentRepository;
import allmart.payservice.application.required.TossConfirmResult;
import allmart.payservice.application.required.TossPayments;
import allmart.payservice.application.required.outbox.OutboxEventRepository;
import allmart.payservice.domain.outbox.PaymentApprovedPayload;
import allmart.payservice.domain.PaymentEventType;
import allmart.payservice.domain.PaymentLedger;
import allmart.payservice.domain.PaymentReadyRequest;
import allmart.payservice.domain.outbox.OutboxEvent;
import allmart.payservice.domain.outbox.PaymentFailedPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import tools.jackson.databind.ObjectMapper;


@Service
@Transactional
@Validated
@RequiredArgsConstructor
public class PaymentApprovalService implements PaymentApproval {

    private static final String AGGREGATE_TYPE_PAYMENT = "PAYMENT";
    private static final String EVENT_APPROVED = "PAYMENT_APPROVED";
    private static final String EVENT_FAILED = "PAYMENT_FAILED";

    private final PaymentRepository paymentRepository;
    private final TossPayments tossPayments;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final PaymentLockPort paymentLockPort;

    @Override
    public PaymentLedger approve(PaymentReadyRequest request, String paymentKey) {

        // 분산 락 획득 (SET NX EX) — 동일 paymentKey 동시 처리 방지
        if (!paymentLockPort.tryLock(paymentKey)) {
            throw new PaymentConflictException(paymentKey);
        }

        try {
            // 중복 방지 (재시도 시 멱등성)
            if (paymentRepository.existsByPaymentKeyAndEventType(paymentKey, PaymentEventType.APPROVED)) {
                return paymentRepository.findByPaymentKeyAndEventType(paymentKey, PaymentEventType.APPROVED)
                        .orElseThrow(() -> new IllegalStateException("Approved ledger not found: " + paymentKey));
            }

            // 토스 승인확인
            TossConfirmResult result = tossPayments.confirm(request, paymentKey);

            // 결과 엔티티, 아웃박스 insert
            return result.approved()
                    ? approveAndPublish(request, paymentKey, result)
                    : failAndPublish(request, result);

        } finally {
            // 처리 완료 후 즉시 락 해제 (TTL 30s 기다리지 않음)
            paymentLockPort.unlock(paymentKey);
        }
    }



    private PaymentLedger approveAndPublish(
            PaymentReadyRequest request,
            String paymentKey,
            TossConfirmResult result
    ) {
        PaymentLedger ledger = paymentRepository.save(
                PaymentLedger.approved(
                        request.tossOrderId(),
                        paymentKey,
                        request.amount(),
                        result.tossStatus(),
                        result.rawJson()
                )
        );

        publishOutbox(
                EVENT_APPROVED,
                ledger.getTossOrderId(),
                PaymentApprovedPayload.from(ledger)
        );

        return ledger;
    }

    private PaymentLedger failAndPublish(
            PaymentReadyRequest request,
            TossConfirmResult result
    ) {
        PaymentLedger ledger = paymentRepository.save(
                PaymentLedger.failed(
                        request.tossOrderId(),
                        request.amount(),
                        result.tossStatus() != null ? result.tossStatus() : "FAILED",
                        result.rawJson()
                )
        );

        publishOutbox(
                EVENT_FAILED,
                ledger.getTossOrderId(),
                PaymentFailedPayload.from(ledger)
        );

        return ledger;
    }

    private void publishOutbox(String eventType, String aggregateId, Object payload) {
        try {
            outboxEventRepository.save(
                    OutboxEvent.create(
                            eventType,
                            AGGREGATE_TYPE_PAYMENT,
                            aggregateId,
                            objectMapper.writeValueAsString(payload)
                    )
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}
