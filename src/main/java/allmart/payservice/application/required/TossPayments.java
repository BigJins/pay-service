package allmart.payservice.application.required;


import allmart.payservice.domain.PaymentReadyRequest;

public interface TossPayments  {
    TossConfirmResult confirm(PaymentReadyRequest request, String paymentKey);
}
