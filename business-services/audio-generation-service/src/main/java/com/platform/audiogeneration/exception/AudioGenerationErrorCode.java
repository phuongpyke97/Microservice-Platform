package com.platform.audiogeneration.exception;

import com.platform.common.core.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AudioGenerationErrorCode implements ErrorCode {
    WALLET_INSUFFICIENT_CREDIT("WALLET_INSUFFICIENT_CREDIT", "Insufficient credits to generate music", HttpStatus.PAYMENT_REQUIRED),
    INVALID_PROMPT("INVALID_PROMPT", "Prompt must be strictly in English and maximum 100 characters", HttpStatus.BAD_REQUEST),
    INVALID_CLIP_DURATION("INVALID_CLIP_DURATION", "Clip duration must be between 40 and 60 seconds", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;

    AudioGenerationErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }
}
