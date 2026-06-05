package com.platform.crbtcampaign.client;

import com.platform.common.core.response.ApiResponse;
import com.platform.crbtcampaign.client.dto.DiyJobResponse;
import com.platform.crbtcampaign.client.fallback.AudioGenerationClientFallback;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "audio-generation-service", fallbackFactory = AudioGenerationClientFallback.class)
public interface AudioGenerationClient {

    @GetMapping("/audio-jobs")
    ApiResponse<List<DiyJobResponse>> getUserJobs(@RequestHeader("Authorization") String authHeader);

    @DeleteMapping("/audio-jobs/{jobId}")
    ApiResponse<Void> deleteJob(@RequestHeader("Authorization") String authHeader, @PathVariable("jobId") Long jobId);
}
