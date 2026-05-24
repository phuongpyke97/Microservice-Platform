package com.platform.common.core.response;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    @Test
    void of_setsErrorFieldsAndTimestamp() {
        ErrorResponse response = ErrorResponse.of("AUTH_USER_NOT_FOUND", "User not found");

        assertThat(response.errorCode()).isEqualTo("AUTH_USER_NOT_FOUND");
        assertThat(response.message()).isEqualTo("User not found");
        assertThat(response.timestamp()).isPositive();
    }
}
