package com.platform.common.core.response;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void successWithData_setsFieldsCorrectly() {
        ApiResponse<String> response = ApiResponse.success("Created", "payload");

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Created");
        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.timestamp()).isPositive();
    }

    @Test
    void successShorthand_defaultMessageIsOK() {
        ApiResponse<Integer> response = ApiResponse.success(42);

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("OK");
        assertThat(response.data()).isEqualTo(42);
    }

    @Test
    void successWithNullData_stillSucceeds() {
        ApiResponse<Object> response = ApiResponse.success(null);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isNull();
    }
}
