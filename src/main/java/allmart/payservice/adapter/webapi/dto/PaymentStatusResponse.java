package allmart.payservice.adapter.webapi.dto;

import allmart.payservice.domain.PaymentEventType;
import allmart.payservice.domain.PaymentLedger;

public record PaymentStatusResponse(
        String tossOrderId,
        PaymentEventType eventType,
        long amount,
        String paymentKey,
        String createdAt
) {

    public static PaymentStatusResponse of(PaymentLedger ledger) {

        return new PaymentStatusResponse(
                ledger.getTossOrderId(),
                ledger.getEventType(),
                ledger.getAmount(),
                ledger.getPaymentKey(),
                ledger.getCreatedAt().toString()
        );
    }
}
