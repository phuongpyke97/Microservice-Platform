package com.platform.common.core.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {
    String code();
    String message();
    HttpStatus status();
}
