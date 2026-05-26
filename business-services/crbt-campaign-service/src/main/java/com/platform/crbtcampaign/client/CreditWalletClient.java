package com.platform.crbtcampaign.client;

import com.platform.common.core.response.ApiResponse;
import com.platform.crbtcampaign.client.dto.WalletAmountRequest;
import com.platform.crbtcampaign.client.dto.WalletResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "credit-wallet-service")
public interface CreditWalletClient {

    @GetMapping("/api/wallet/internal/{userId}/balance")
    ApiResponse<WalletResponse> getBalance(@PathVariable("userId") Long userId);

    @PostMapping("/api/wallet/{userId}/deduct")
    ApiResponse<WalletResponse> deduct(@PathVariable("userId") Long userId, @RequestBody WalletAmountRequest request);

    @PostMapping("/api/wallet/{userId}/add")
    ApiResponse<WalletResponse> add(@PathVariable("userId") Long userId, @RequestBody WalletAmountRequest request);
}
