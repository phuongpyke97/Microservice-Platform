package com.platform.payment.exception;

import com.platform.common.core.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PaymentErrorCode implements ErrorCode {
    PAY_TRANSACTION_NOT_FOUND("PAY_TRANSACTION_NOT_FOUND", "Transaction not found", HttpStatus.NOT_FOUND),
    PAY_DUPLICATE_REQUEST("PAY_DUPLICATE_REQUEST", "Duplicate payment request", HttpStatus.CONFLICT),
    PAY_MPS_UNAVAILABLE("PAY_MPS_UNAVAILABLE", "Payment provider unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    PAY_MPS_REJECTED("PAY_MPS_REJECTED", "Payment rejected by provider", HttpStatus.PAYMENT_REQUIRED),
    PAY_INVALID_AMOUNT("PAY_INVALID_AMOUNT", "Amount must be positive", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;

    PaymentErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public HttpStatus status() { return status; }
}
