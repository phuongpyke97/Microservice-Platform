package com.platform.common.core.exception;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {
    COMMON_INTERNAL_ERROR("COMMON_INTERNAL_ERROR", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    SYSTEM_BUSY("SYSTEM_BUSY", "Hệ thống đang bận. Vui lòng thử lại sau!", HttpStatus.INTERNAL_SERVER_ERROR),
    COMMON_VALIDATION_FAILED("COMMON_VALIDATION_FAILED", "Validation failed", HttpStatus.BAD_REQUEST),
    COMMON_DOWNSTREAM_ERROR("COMMON_DOWNSTREAM_ERROR", "Downstream service error", HttpStatus.BAD_GATEWAY),
    COMMON_NOT_FOUND("COMMON_NOT_FOUND", "Resource not found", HttpStatus.NOT_FOUND),
    COMMON_UNAUTHORIZED("COMMON_UNAUTHORIZED", "Unauthorized", HttpStatus.UNAUTHORIZED),
    COMMON_BAD_REQUEST("COMMON_BAD_REQUEST", "Bad request", HttpStatus.BAD_REQUEST),
    COMMON_FORBIDDEN("COMMON_FORBIDDEN", "Forbidden request", HttpStatus.FORBIDDEN);

    private final String code;
    private final String message;
    private final HttpStatus status;

    CommonErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public HttpStatus status() { return status; }
}
