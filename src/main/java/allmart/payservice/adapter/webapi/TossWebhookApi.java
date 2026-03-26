package allmart.payservice.adapter.webapi;

import allmart.payservice.application.PaymentWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequiredArgsConstructor
@Log4j2
public class TossWebhookApi {

    private final PaymentWebhookService paymentWebhookService;
    private final ObjectMapper objectMapper;

    @PostMapping("/webhooks/toss")
    public ResponseEntity<Void> handle(@RequestBody JsonNode body) throws Exception {

        String rawJson = objectMapper.writeValueAsString(body);

        JsonNode data = body.path("data");

        String paymentKey = data.path("paymentKey").asText(null);
        String tossOrderId = data.path("orderId").asText(null);
        long amount = data.path("totalAmount").asLong(0);
        String tossStatus = data.path("status").asText(null);

        log.info("TOSS WEBHOOK status={}, tossOrderId={}, paymentKey={}", tossStatus, tossOrderId, paymentKey);

        paymentWebhookService.handle(tossOrderId, paymentKey, amount, tossStatus, rawJson);

        return ResponseEntity.ok().build();

    }
}
