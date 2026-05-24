package com.platform.creditwallet.exception;

import com.platform.common.core.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum WalletErrorCode implements ErrorCode {
    WALLET_NOT_FOUND("WALLET_NOT_FOUND", "Wallet not found", HttpStatus.NOT_FOUND),
    WALLET_INSUFFICIENT_CREDIT("WALLET_INSUFFICIENT_CREDIT", "Insufficient credit", HttpStatus.BAD_REQUEST),
    WALLET_LOCK_TIMEOUT("WALLET_LOCK_TIMEOUT", "Could not acquire wallet lock", HttpStatus.CONFLICT),
    WALLET_INVALID_AMOUNT("WALLET_INVALID_AMOUNT", "Amount must be positive", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;

    WalletErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public HttpStatus status() { return status; }
}
