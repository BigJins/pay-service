package allmart.payservice.adapter.webapi;

import allmart.payservice.application.PaymentConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentConflictException.class)
    public ResponseEntity<String> handlePaymentConflict(PaymentConflictException e) {
        // 동일 paymentKey 동시 요청 → 선착순 1개만 처리, 나머지 409
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
}