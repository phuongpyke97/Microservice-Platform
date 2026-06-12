package com.platform.crbtcampaign.client.fallback;

import com.platform.common.core.response.ApiResponse;
import com.platform.crbtcampaign.client.CrbtCoreAdapterClient;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class CrbtCoreAdapterClientFallback implements FallbackFactory<CrbtCoreAdapterClient> {

    @Override
    public CrbtCoreAdapterClient create(Throwable cause) {
        return urls -> ApiResponse.error("ADAPTER_UNAVAILABLE", "CRBT core adapter service is down");
    }
}
