package allmart.payservice.application;

/**
 * 동일 paymentKey에 대해 결제 처리가 이미 진행 중일 때 발생.
 * Redis 분산 락 획득 실패 → 409 Conflict 응답으로 변환된다.
 */
public class PaymentConflictException extends RuntimeException {

    public PaymentConflictException(String paymentKey) {
        super("이미 처리 중인 결제입니다: " + paymentKey);
    }
}