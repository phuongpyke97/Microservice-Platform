package com.platform.crbtcampaign.client.fallback;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.common.core.response.ApiResponse;
import com.platform.crbtcampaign.client.AudioGenerationClient;
import com.platform.crbtcampaign.client.dto.DiyJobResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class AudioGenerationClientFallback implements FallbackFactory<AudioGenerationClient> {

    @Override
    public AudioGenerationClient create(Throwable cause) {
        return new AudioGenerationClient() {
            @Override
            public ApiResponse<List<DiyJobResponse>> getUserJobs(String authHeader) {
                // Fallback returns empty list
                return ApiResponse.success(List.of());
            }

            @Override
            public ApiResponse<Void> deleteJob(String authHeader, Long jobId) {
                throw new BaseException(CommonErrorCode.SYSTEM_BUSY, "Cannot contact audio generation service for deletion");
            }
        };
    }
}
