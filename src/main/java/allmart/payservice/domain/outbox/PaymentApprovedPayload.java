package allmart.payservice.domain.outbox;

import allmart.payservice.domain.PaymentLedger;

public record PaymentApprovedPayload(
        String tossOrderId,
        long amount,
        String paymentKey
) {
    public static PaymentApprovedPayload from(PaymentLedger ledger) {
        return new PaymentApprovedPayload(
                ledger.getTossOrderId(),
                ledger.getAmount(),
                ledger.getPaymentKey()
        );
    }
}