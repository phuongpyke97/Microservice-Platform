package com.platform.crbtcommunitylibrary.client;

import com.platform.common.core.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "crbt-campaign-service")
public interface CampaignClient {

    @GetMapping("/internal/campaign/lyria-history/{id}")
    ApiResponse<UserLyriaHistoryResponse> getLyriaHistory(@PathVariable("id") Long id);

    record UserLyriaHistoryResponse(
        Long id,
        Long userId,
        String msisdn,
        String title,
        String genre,
        String mood,
        String instrument,
        String audioUrl,
        int durationSeconds
    ) {}
}
