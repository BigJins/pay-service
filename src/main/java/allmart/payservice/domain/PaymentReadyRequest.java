package allmart.payservice.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentReadyRequest(
        @NotNull String tossOrderId,
        @Positive long amount
//        @NotBlank String idempotencyKey
) {
}
