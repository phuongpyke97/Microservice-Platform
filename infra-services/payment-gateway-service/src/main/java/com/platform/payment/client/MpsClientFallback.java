package com.platform.payment.client;

import org.springframework.stereotype.Component;

@Component
public class MpsClientFallback implements MpsClient {
    @Override
    public MpsChargeResponse charge(MpsChargeRequest request) {
        return new MpsChargeResponse(false, null, "MPS fallback");
    }
}
