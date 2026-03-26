package allmart.payservice.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentLedgerTest {
    PaymentLedger paymentLedger;
    String tossOrderId;
    long amount;
//    String idempotencyKey;
    PaymentReadyRequest paymentReadyRequest;
    String paymentKey;
    String rawJson;

    @BeforeEach
    void setUp() {
        tossOrderId = "dsfadsfdsafsdf";
        amount = 1000L;
//        idempotencyKey = UUID.randomUUID().toString();
        paymentReadyRequest = new PaymentReadyRequest(tossOrderId, amount);
        paymentKey = UUID.randomUUID().toString();
        rawJson = "Random";
    }

//    @Test
//    void approved() {
//        PaymentLedger ledger = PaymentLedger.approved(tossOrderId, paymentKey, amount, rawJson);
//
//        assertThat(ledger.getTossOrderId()).isEqualTo(tossOrderId);
//        assertThat(ledger.getPaymentKey()).isEqualTo(paymentKey);
//        assertThat(ledger.getAmount()).isEqualTo(amount);
//        assertThat(ledger.getEventType()).isEqualTo(PaymentEventType.APPROVED);
//        assertThat(ledger.getRawResponseJson()).isEqualTo(rawJson);
//    }
//
//    @Test
//    void failed() {
//        PaymentLedger ledger = PaymentLedger.failed(tossOrderId, amount, rawJson);
//
//        assertThat(ledger.getTossOrderId()).isEqualTo(tossOrderId);
//        assertThat(ledger.getPaymentKey()).isNull(); // 실패는 paymentKey가 없을 수 있음(정책에 따라)
//        assertThat(ledger.getAmount()).isEqualTo(amount);
//        assertThat(ledger.getEventType()).isEqualTo(PaymentEventType.FAILED);
//        assertThat(ledger.getRawResponseJson()).isEqualTo(rawJson);
//    }
//
//    @Test
//    void approved_throws_when_paymentKey_blank() {
//        assertThatThrownBy(() -> PaymentLedger.approved(tossOrderId, "   ", amount, rawJson))
//                .isInstanceOf(IllegalArgumentException.class)
//                .hasMessageContaining("paymentKey");
//
//    }
//
//    @Test
//    void approved_throws_when_amount_invalid() {
//        assertThatThrownBy(() -> PaymentLedger.approved(tossOrderId, paymentKey, 0L, rawJson))
//                .isInstanceOf(IllegalArgumentException.class)
//                .hasMessageContaining("amount");
//    }
//
//    @Test
//    void failed_throws_when_amount_invalid() {
//        assertThatThrownBy(() -> PaymentLedger.failed(tossOrderId, 0L, rawJson))
//                .isInstanceOf(IllegalArgumentException.class)
//                .hasMessageContaining("amount");
//    }

//    @Test
//    void request() {
//        paymentLedger = PaymentLedger.requested(paymentReadyRequest);
//
//        assertThat(paymentLedger.getEventType()).isEqualTo(PaymentEventType.REQUESTED);
//    }

//    @Test
//    void approved() {
//        paymentLedger = PaymentLedger.approved(paymentReadyRequest, paymentKey, rawJson);
//
//        assertThat(paymentLedger.getEventType()).isEqualTo(PaymentEventType.APPROVED);
//    }
//
//    @Test
//    void fail() {
//        paymentLedger = PaymentLedger.fail(paymentReadyRequest, rawJson);
//
//        assertThat(paymentLedger.getEventType()).isEqualTo(PaymentEventType.FAILED);
//    }
}