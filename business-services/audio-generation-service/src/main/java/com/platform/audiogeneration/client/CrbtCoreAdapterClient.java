package com.platform.audiogeneration.client;

import com.platform.common.core.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;

@FeignClient(name = "crbt-core-adapter", fallback = CrbtCoreAdapterClientFallback.class)
public interface CrbtCoreAdapterClient {
    @PostMapping("/api/internal/ringtone-assignments/active-check")
    ApiResponse<List<String>> activeCheck(@RequestBody List<String> urls);
}

class CrbtCoreAdapterClientFallback implements CrbtCoreAdapterClient {
    @Override
    public ApiResponse<List<String>> activeCheck(List<String> urls) {
        return ApiResponse.error("CORE_ADAPTER_UNAVAILABLE", "CRBT core adapter service is currently down");
    }
}
