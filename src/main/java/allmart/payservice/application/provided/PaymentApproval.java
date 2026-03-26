package allmart.payservice.application.provided;

import allmart.payservice.domain.PaymentLedger;
import allmart.payservice.domain.PaymentReadyRequest;
import jakarta.validation.Valid;

public interface PaymentApproval {

    PaymentLedger approve(@Valid PaymentReadyRequest request, String paymentKey);
}
