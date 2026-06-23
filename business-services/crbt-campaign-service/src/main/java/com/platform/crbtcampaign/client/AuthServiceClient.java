package com.platform.crbtcampaign.client;

import com.platform.common.core.response.PageResponse;
import com.platform.crbtcampaign.client.dto.UserCreditResponse;
import com.platform.crbtcampaign.client.dto.UserResponse;
import com.platform.crbtcampaign.client.fallback.AuthServiceClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "auth-service", fallbackFactory = AuthServiceClientFallback.class)
public interface AuthServiceClient {

    @GetMapping("/internal/crbt/user-credit/{msisdn}")
    UserCreditResponse getUserCredit(@PathVariable("msisdn") String msisdn);

    @GetMapping("/internal/crbt/users")
    PageResponse<UserResponse> getUsers(
            @RequestParam(value = "msisdn", required = false) String msisdn,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    );
}
