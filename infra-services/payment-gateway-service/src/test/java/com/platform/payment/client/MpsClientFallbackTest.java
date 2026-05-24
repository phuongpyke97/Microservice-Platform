package com.platform.payment.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MpsClientFallbackTest {

    @Test
    void charge_returnsUnavailableResult() {
        MpsChargeResponse response = new MpsClientFallback()
                .charge(new MpsChargeRequest("959123", 1000, "PKG30", "idem-1"));

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("MPS fallback");
    }
}
