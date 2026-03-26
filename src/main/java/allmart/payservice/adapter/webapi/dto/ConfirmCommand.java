package allmart.payservice.adapter.webapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ConfirmCommand(
        @NotBlank String tossOrderId,
        @NotBlank String paymentKey,
        @Positive long amount
) {
}
