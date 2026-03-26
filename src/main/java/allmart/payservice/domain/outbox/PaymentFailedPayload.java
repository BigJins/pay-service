package allmart.payservice.domain.outbox;

import allmart.payservice.domain.PaymentLedger;


public record PaymentFailedPayload(
        String tossOrderId,
        long amount,
        String tossStatus,
        String reason
) {
    public static PaymentFailedPayload from(PaymentLedger ledger) {
        return new PaymentFailedPayload(
                ledger.getTossOrderId(),
                ledger.getAmount(),
                ledger.getTossStatus(),
                "PAYMENT_FAILED"
        );
    }
}