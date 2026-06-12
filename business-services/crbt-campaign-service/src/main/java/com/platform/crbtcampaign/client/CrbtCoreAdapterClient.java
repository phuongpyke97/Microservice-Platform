package com.platform.crbtcampaign.client;

import com.platform.common.core.response.ApiResponse;
import com.platform.crbtcampaign.client.fallback.CrbtCoreAdapterClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;

@FeignClient(name = "crbt-core-adapter", fallbackFactory = CrbtCoreAdapterClientFallback.class)
public interface CrbtCoreAdapterClient {

    @PostMapping("/api/internal/ringtone-assignments/active-check")
    ApiResponse<List<String>> activeCheck(@RequestBody List<String> urls);
}
