package allmart.payservice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static java.util.Objects.requireNonNull;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "payment_ledger",
        indexes = {
                @Index(name = "idx_pl_toss_order_id", columnList = "toss_order_id"),
                @Index(name = "idx_pl_payment_key", columnList = "payment_key"),
                @Index(name = "idx_pl_created_at", columnList = "created_at")
        }
)
public class PaymentLedger {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "toss_order_id", nullable = false, length = 64)
    private String tossOrderId;

    @Column(name = "payment_key", length = 200, unique = true)
    private String paymentKey; // 승인 이후에만 존재 가능

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private PaymentEventType eventType;

    @Column(nullable = false)
    private long amount;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String rawResponseJson;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name="toss_status", nullable=false, length=30)
    private String tossStatus;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }



    public static PaymentLedger fromWebhook(
            String tossOrderId,
            String paymentKey,
            long amount,
            String tossStatus,
            PaymentEventType eventType,
            String rawJson
    ) {
        validate(tossOrderId, amount);

        PaymentLedger ledger = new PaymentLedger();
        ledger.tossOrderId = tossOrderId;
        ledger.paymentKey = paymentKey;         // 실패에도 있을 수 있어서 nullable 허용
        ledger.amount = amount;
        ledger.rawResponseJson = rawJson;

        ledger.eventType = eventType;
        ledger.tossStatus = tossStatus;

        return ledger;
    }

    private static void validate(String tossOrderId, long amount) {
        if (tossOrderId == null || tossOrderId.isBlank()) throw new IllegalArgumentException("tossOrderId required");
        if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
    }

    private static void validatePaymentKey(String paymentKey) {
        if (paymentKey == null || paymentKey.isBlank()) throw new IllegalArgumentException("paymentKey must not be null or empty");
    }

    public static PaymentLedger approved(String tossOrderId, String paymentKey, long amount, String tossStatus, String rawJson) {
        validate(tossOrderId, amount);
        validatePaymentKey(paymentKey);
        requireNonNull(tossStatus, " tossStatus must not be null");

        PaymentLedger l = new PaymentLedger();
        l.tossOrderId = tossOrderId;
        l.paymentKey = paymentKey;
        l.amount = amount;
        l.tossStatus = tossStatus;
        l.rawResponseJson = rawJson;
        l.eventType = PaymentEventType.APPROVED;
        return l;
    }

    public static PaymentLedger failed(String tossOrderId, long amount, String tossStatus, String rawJson) {
        validate(tossOrderId, amount);
        requireNonNull(tossStatus, " tossStatus must not be null");

        PaymentLedger l = new PaymentLedger();
        l.tossOrderId = tossOrderId;
        l.amount = amount;
        l.tossStatus = tossStatus;
        l.rawResponseJson = rawJson;
        l.eventType = PaymentEventType.FAILED;
        return l;
    }
}
