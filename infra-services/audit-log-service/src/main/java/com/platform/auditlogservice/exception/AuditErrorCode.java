package com.platform.auditlogservice.exception;

import com.platform.common.core.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuditErrorCode implements ErrorCode {
    AUDIT_INVALID_DATE_RANGE("AUDIT_INVALID_DATE_RANGE", "fromTs must be before toTs", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;

    AuditErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public HttpStatus status() { return status; }
}
