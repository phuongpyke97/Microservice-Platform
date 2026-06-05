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

            @Override
            public ApiResponse<List<DiyJobResponse>> searchJobsAdmin(String authHeader, String startTime, String endTime, Long userId, String msisdn, String search, int page, int size) {
                return ApiResponse.success(List.of());
            }

            @Override
            public ApiResponse<DiyJobResponse> getJobAdmin(String authHeader, Long jobId) {
                throw new BaseException(CommonErrorCode.SYSTEM_BUSY, "Cannot contact audio generation service");
            }

            @Override
            public ApiResponse<DiyJobResponse> createJobAdmin(String authHeader, Long userId, com.platform.crbtcampaign.client.dto.DiyJobRequest request) {
                throw new BaseException(CommonErrorCode.SYSTEM_BUSY, "Cannot contact audio generation service");
            }

            @Override
            public ApiResponse<DiyJobResponse> updateJobAdmin(String authHeader, Long jobId, DiyJobResponse request) {
                throw new BaseException(CommonErrorCode.SYSTEM_BUSY, "Cannot contact audio generation service");
            }

            @Override
            public ApiResponse<Void> deleteJobAdmin(String authHeader, Long jobId, boolean hard) {
                throw new BaseException(CommonErrorCode.SYSTEM_BUSY, "Cannot contact audio generation service");
            }
        };
    }
}
