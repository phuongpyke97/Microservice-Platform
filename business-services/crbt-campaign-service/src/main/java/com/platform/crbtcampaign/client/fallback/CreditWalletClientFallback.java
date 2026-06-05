package com.platform.crbtcampaign.client.fallback;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.common.core.response.ApiResponse;
import com.platform.crbtcampaign.client.CreditWalletClient;
import com.platform.crbtcampaign.client.dto.WalletAmountRequest;
import com.platform.crbtcampaign.client.dto.WalletResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class CreditWalletClientFallback implements FallbackFactory<CreditWalletClient> {

    @Override
    public CreditWalletClient create(Throwable cause) {
        return new CreditWalletClient() {
            @Override
            public ApiResponse<WalletResponse> getBalance(Long userId) {
                throw new BaseException(CommonErrorCode.COMMON_DOWNSTREAM_ERROR);
            }

            @Override
            public ApiResponse<WalletResponse> deduct(Long userId, WalletAmountRequest request) {
                throw new BaseException(CommonErrorCode.COMMON_DOWNSTREAM_ERROR);
            }

            @Override
            public ApiResponse<WalletResponse> add(Long userId, WalletAmountRequest request) {
                throw new BaseException(CommonErrorCode.COMMON_DOWNSTREAM_ERROR);
            }
        };
    }
}
