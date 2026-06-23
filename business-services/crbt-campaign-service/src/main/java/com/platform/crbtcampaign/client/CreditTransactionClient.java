package com.platform.crbtcampaign.client;

import com.platform.common.core.response.ApiResponse;
import com.platform.crbtcampaign.client.dto.UserCreditStats;
import com.platform.crbtcampaign.client.fallback.CreditTransactionClientFallback;
import java.util.List;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "crbt-credit-transaction-service", fallbackFactory = CreditTransactionClientFallback.class)
public interface CreditTransactionClient {

    @PostMapping("/credit-transactions/internal/stats")
    ApiResponse<Map<Long, UserCreditStats>> getStats(@RequestBody List<Long> userIds);

    @PostMapping("/credit-transactions/internal/stats/sum")
    ApiResponse<UserCreditStats> sumStats(@RequestBody List<Long> userIds);
}
