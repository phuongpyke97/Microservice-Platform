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

    @org.springframework.web.bind.annotation.PutMapping("/audio-jobs/{jobId}")
    ApiResponse<DiyJobResponse> updateJob(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("jobId") Long jobId,
            @org.springframework.web.bind.annotation.RequestBody DiyJobResponse request
    );

    @GetMapping("/audio-jobs/admin/search")
    ApiResponse<List<DiyJobResponse>> searchJobsAdmin(
            @RequestHeader("Authorization") String authHeader,
            @org.springframework.web.bind.annotation.RequestParam(value = "startTime", required = false) String startTime,
            @org.springframework.web.bind.annotation.RequestParam(value = "endTime", required = false) String endTime,
            @org.springframework.web.bind.annotation.RequestParam(value = "userId", required = false) Long userId,
            @org.springframework.web.bind.annotation.RequestParam(value = "msisdn", required = false) String msisdn,
            @org.springframework.web.bind.annotation.RequestParam(value = "search", required = false) String search,
            @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "10") int size
    );

    @GetMapping("/audio-jobs/admin/{jobId}")
    ApiResponse<DiyJobResponse> getJobAdmin(@RequestHeader("Authorization") String authHeader, @PathVariable("jobId") Long jobId);

    @org.springframework.web.bind.annotation.PostMapping("/audio-jobs/admin")
    ApiResponse<DiyJobResponse> createJobAdmin(
            @RequestHeader("Authorization") String authHeader,
            @org.springframework.web.bind.annotation.RequestParam(value = "userId", required = false) Long userId,
            @org.springframework.web.bind.annotation.RequestBody com.platform.crbtcampaign.client.dto.DiyJobRequest request
    );

    @org.springframework.web.bind.annotation.PutMapping("/audio-jobs/admin/{jobId}")
    ApiResponse<DiyJobResponse> updateJobAdmin(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("jobId") Long jobId,
            @org.springframework.web.bind.annotation.RequestBody DiyJobResponse request
    );

    @DeleteMapping("/audio-jobs/admin/{jobId}")
    ApiResponse<Void> deleteJobAdmin(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("jobId") Long jobId,
            @org.springframework.web.bind.annotation.RequestParam(value = "hard", defaultValue = "false") boolean hard
    );
}
