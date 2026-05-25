package com.platform.crbtcampaign.client.fallback;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.crbtcampaign.client.AuthServiceClient;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class AuthServiceClientFallback implements FallbackFactory<AuthServiceClient> {

    @Override
    public AuthServiceClient create(Throwable cause) {
        return msisdn -> {
            throw new BaseException(CommonErrorCode.COMMON_DOWNSTREAM_ERROR);
        };
    }
}
