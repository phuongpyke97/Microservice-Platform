package com.platform.crbtcampaign.client;

import com.platform.common.core.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "crbt-community-library", fallback = LibraryClientFallback.class)
public interface LibraryClient {
    @GetMapping("/library/ringtones/random")
    ApiResponse<Object> getRandomRingtone(@RequestParam("genre") String genre);

    @GetMapping("/library/ringtones/fallback")
    ApiResponse<Object> getFallbackRingtone(
        @RequestParam("genre") String genre,
        @RequestParam("mood") String mood,
        @RequestParam("instrument") String instrument);
}

class LibraryClientFallback implements LibraryClient {
    @Override
    public ApiResponse<Object> getRandomRingtone(String genre) {
        return ApiResponse.error("LIBRARY_UNAVAILABLE", "Community library is down");
    }

    @Override
    public ApiResponse<Object> getFallbackRingtone(String genre, String mood, String instrument) {
        return ApiResponse.error("LIBRARY_UNAVAILABLE", "Community library is down");
    }
}
