package com.platform.crbtcampaign.client.fallback;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.common.core.response.PageResponse;
import com.platform.crbtcampaign.client.AuthServiceClient;
import com.platform.crbtcampaign.client.dto.UserResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class AuthServiceClientFallback implements FallbackFactory<AuthServiceClient> {

    @Override
    public AuthServiceClient create(Throwable cause) {
        return new AuthServiceClient() {
            @Override
            public com.platform.crbtcampaign.client.dto.UserCreditResponse getUserCredit(String msisdn) {
                throw new BaseException(CommonErrorCode.COMMON_DOWNSTREAM_ERROR);
            }

            @Override
            public PageResponse<UserResponse> getUsers(String msisdn, String status, String startTime, String endTime, int page, int size) {
                throw new BaseException(CommonErrorCode.COMMON_DOWNSTREAM_ERROR);
            }

            @Override
            public java.util.List<Long> getUserIds(String msisdn, String status, String startTime, String endTime) {
                throw new BaseException(CommonErrorCode.COMMON_DOWNSTREAM_ERROR);
            }
        };
    }
}
