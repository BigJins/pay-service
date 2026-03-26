package allmart.payservice.adapter.webapi;

import allmart.payservice.adapter.webapi.dto.ConfirmCommand;
import allmart.payservice.application.PaymentApprovalService;
import allmart.payservice.domain.PaymentReadyRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequiredArgsConstructor
public class PaymentConfirmApi {

    private final PaymentApprovalService paymentApprovalService;

    @PostMapping("/api/payments/toss/confirm")
    public ResponseEntity<Void> confirm(@RequestBody @Valid ConfirmCommand cmd) {
        PaymentReadyRequest ready = new PaymentReadyRequest(cmd.tossOrderId(), cmd.amount());
        paymentApprovalService.approve(ready, cmd.paymentKey());
        // 여기서 APPROVED/FAILED 저장됨
        return ResponseEntity.ok().build();
    }
}
