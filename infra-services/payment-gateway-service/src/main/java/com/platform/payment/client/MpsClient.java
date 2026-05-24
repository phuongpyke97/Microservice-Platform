package com.platform.payment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "mpsClient", url = "${mps.endpoint}", configuration = com.platform.payment.config.FeignConfig.class, fallback = MpsClientFallback.class)
public interface MpsClient {

    @PostMapping("/charge")
    MpsChargeResponse charge(@RequestBody MpsChargeRequest request);
}
