package allmart.payservice.adapter.webapi;

import allmart.payservice.adapter.webapi.dto.PaymentStatusResponse;
import allmart.payservice.application.required.PaymentRepository;
import allmart.payservice.domain.PaymentLedger;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequiredArgsConstructor
public class PaymentQueryApi {

    private final PaymentRepository paymentRepository;

    @GetMapping("/api/payments/status")
    public PaymentStatusResponse status(@RequestParam String tossOrderId) {
        PaymentLedger ledger = paymentRepository.findTopByTossOrderIdOrderByCreatedAtDesc(tossOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));

        return PaymentStatusResponse.of(ledger);
    }
}
