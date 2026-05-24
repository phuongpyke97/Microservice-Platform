package com.platform.common.core.exception;

import com.platform.common.core.response.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleBaseException_returnsErrorCodeStatusAndMessage() {
        BaseException ex = new BaseException(CommonErrorCode.COMMON_NOT_FOUND);

        ResponseEntity<ErrorResponse> resp = handler.handleBaseException(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().errorCode()).isEqualTo("COMMON_NOT_FOUND");
        assertThat(resp.getBody().message()).isEqualTo("Resource not found");
    }

    @Test
    void handleUnknownException_returns500WithInternalErrorCode() {
        Exception ex = new RuntimeException("boom");

        ResponseEntity<ErrorResponse> resp = handler.handleException(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().errorCode()).isEqualTo("COMMON_INTERNAL_ERROR");
    }
}
