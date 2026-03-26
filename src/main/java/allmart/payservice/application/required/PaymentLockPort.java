package allmart.payservice.application.required;

public interface PaymentLockPort {

    /**
     * paymentKey 기준으로 분산 락 획득 시도.
     *
     * @return true = 락 획득 성공 (이 스레드가 처리 진행), false = 이미 다른 스레드가 처리 중
     */
    boolean tryLock(String paymentKey);

    void unlock(String paymentKey);
}