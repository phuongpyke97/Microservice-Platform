package com.platform.common.core.response;

public record ErrorResponse(String errorCode, String message, long timestamp) {

    public static ErrorResponse of(String errorCode, String message) {
        return new ErrorResponse(errorCode, message, System.currentTimeMillis());
    }
}
