package com.platform.crbtcampaign.client;

import com.platform.crbtcampaign.client.dto.UserCreditResponse;
import com.platform.crbtcampaign.client.fallback.AuthServiceClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "auth-service", fallbackFactory = AuthServiceClientFallback.class)
public interface AuthServiceClient {

    @GetMapping("/internal/crbt/user-credit/{msisdn}")
    UserCreditResponse getUserCredit(@PathVariable("msisdn") String msisdn);
}
