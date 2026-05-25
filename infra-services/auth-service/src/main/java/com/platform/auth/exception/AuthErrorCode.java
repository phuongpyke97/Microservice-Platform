package com.platform.auth.exception;

import com.platform.common.core.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {
    AUTH_USER_NOT_FOUND("AUTH_USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND),
    AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", "Invalid credentials", HttpStatus.UNAUTHORIZED),
    AUTH_EMAIL_ALREADY_EXISTS("AUTH_EMAIL_ALREADY_EXISTS", "Email already registered", HttpStatus.CONFLICT),
    AUTH_MSISDN_ALREADY_EXISTS("AUTH_MSISDN_ALREADY_EXISTS", "MSISDN already registered", HttpStatus.CONFLICT),
    AUTH_ACCOUNT_LOCKED("AUTH_ACCOUNT_LOCKED", "Account is locked", HttpStatus.FORBIDDEN),
    AUTH_TOKEN_INVALID("AUTH_TOKEN_INVALID", "Token is invalid or expired", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_REFRESH_FAILED("AUTH_TOKEN_REFRESH_FAILED", "Token refresh failed", HttpStatus.UNAUTHORIZED),
    AUTH_CRBT_TOKEN_INVALID("AUTH_CRBT_TOKEN_INVALID", "CRBT token is invalid or malformed", HttpStatus.UNAUTHORIZED),
    AUTH_CRBT_TOKEN_EXPIRED("AUTH_CRBT_TOKEN_EXPIRED", "CRBT token has expired", HttpStatus.UNAUTHORIZED),
    AUTH_CRBT_SUBSCRIBER_INACTIVE("AUTH_CRBT_SUBSCRIBER_INACTIVE", "CRBT subscriber is not active", HttpStatus.FORBIDDEN),
    AUTH_CRBT_TOKEN_MISSING_MSISDN("AUTH_CRBT_TOKEN_MISSING_MSISDN", "CRBT token missing phone/subject claim", HttpStatus.BAD_REQUEST),
    AUTH_CRBT_PROVISION_FAILED("AUTH_CRBT_PROVISION_FAILED", "Failed to provision CRBT subscriber", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;

    AuthErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public HttpStatus status() { return status; }
}
