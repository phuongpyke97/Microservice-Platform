package com.platform.fileservice.exception;

import com.platform.common.core.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum FileErrorCode implements ErrorCode {
    FILE_NOT_FOUND("FILE_NOT_FOUND", "File not found", HttpStatus.NOT_FOUND),
    FILE_TOO_LARGE("FILE_TOO_LARGE", "File exceeds 15MB limit", HttpStatus.BAD_REQUEST),
    FILE_TYPE_NOT_ALLOWED("FILE_TYPE_NOT_ALLOWED", "File type not allowed", HttpStatus.BAD_REQUEST),
    FILE_UPLOAD_FAILED("FILE_UPLOAD_FAILED", "Failed to upload file to storage", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_ALREADY_DELETED("FILE_ALREADY_DELETED", "File is already deleted", HttpStatus.GONE),
    FILE_NOT_IN_TEMP("FILE_NOT_IN_TEMP", "File must be in UPLOADED state to confirm", HttpStatus.CONFLICT),
    FILE_INVALID_TARGET_BUCKET("FILE_INVALID_TARGET_BUCKET", "Target bucket is invalid", HttpStatus.BAD_REQUEST),
    AUDIO_HAS_VOCAL("AUDIO_HAS_VOCAL", "Mp3 đang có vocal", HttpStatus.BAD_REQUEST),
    INVALID_AUDIO_DURATION("INVALID_AUDIO_DURATION", "Audio duration must be between 40 seconds and 5 minutes", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;

    FileErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public HttpStatus status() { return status; }
}
