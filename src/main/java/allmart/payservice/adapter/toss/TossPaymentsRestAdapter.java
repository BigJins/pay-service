package allmart.payservice.adapter.toss;

import allmart.payservice.application.required.TossConfirmResult;
import allmart.payservice.application.required.TossPayments;
import allmart.payservice.domain.PaymentReadyRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.util.retry.Retry;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Profile("!local")
@Component
@RequiredArgsConstructor
public class TossPaymentsRestAdapter implements TossPayments {

    private final TossProperties tossProperties;
    private final TossRetryProperties tossRetryProperties;
    private final WebClient tossWebClient;
    private final ObjectMapper objectMapper;

    @Override
    public TossConfirmResult confirm(PaymentReadyRequest request, String paymentKey) {
        Map<String, Object> body = Map.of(
                "paymentKey", paymentKey,
                "orderId", request.tossOrderId(),
                "amount", request.amount()
        );

        try {
            String raw = tossWebClient.post()
                    .uri("/v1/payments/confirm")
                    .header(HttpHeaders.AUTHORIZATION, basicAuth(tossProperties.getSecretKey()))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(body)
                    // 성공/실패 응답 모두 body 그대로 수신 (4xx/5xx도 예외 없이)
                    .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty(""))
                    // 네트워크 오류(연결 실패, 타임아웃)에 한해 재시도 — Config Server에서 런타임 변경 가능
                    .retryWhen(Retry.backoff(
                                    tossRetryProperties.getRetry().getMaxAttempts(),
                                    Duration.ofMillis(tossRetryProperties.getRetry().getBackoffMs()))
                            .filter(e -> e instanceof WebClientRequestException))
                    .block(Duration.ofSeconds(tossRetryProperties.getTimeoutSeconds()));

            String tossStatus = extractStatus(raw);
            boolean isApproved = "DONE".equalsIgnoreCase(tossStatus);

            return isApproved
                    ? TossConfirmResult.approved(tossStatus, raw)
                    : TossConfirmResult.failed(tossStatus != null ? tossStatus : "FAILED", raw);

        } catch (Exception e) {
            String raw = "{\"error\":\"" + e.getClass().getSimpleName() + ":" + e.getMessage() + "\"}";
            return TossConfirmResult.failed("FAILED", raw);
        }
    }

    private String extractStatus(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return null;
        try {
            return objectMapper.readTree(rawJson).get("status").asText(null);
        } catch (Exception ignore) {
            return null;
        }
    }

    private String basicAuth(String secretKey) {
        String token = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }
}
