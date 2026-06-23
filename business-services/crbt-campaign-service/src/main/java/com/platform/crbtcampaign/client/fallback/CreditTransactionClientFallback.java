package com.platform.crbtcampaign.client.fallback;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.common.core.response.ApiResponse;
import com.platform.crbtcampaign.client.CreditTransactionClient;
import com.platform.crbtcampaign.client.dto.UserCreditStats;
import java.util.List;
import java.util.Map;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class CreditTransactionClientFallback implements FallbackFactory<CreditTransactionClient> {

    @Override
    public CreditTransactionClient create(Throwable cause) {
        return new CreditTransactionClient() {
            @Override
            public ApiResponse<Map<Long, UserCreditStats>> getStats(List<Long> userIds) {
                throw new BaseException(CommonErrorCode.COMMON_DOWNSTREAM_ERROR);
            }

            @Override
            public ApiResponse<UserCreditStats> sumStats(List<Long> userIds) {
                throw new BaseException(CommonErrorCode.COMMON_DOWNSTREAM_ERROR);
            }
        };
    }
}
