package com.platform.crbtcampaign.client;

import com.platform.common.core.response.ApiResponse;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
@EnableFeignClients
@FeignClient(name = "crbt-community-library", fallback = LibraryClientFallback.class)
public interface LibraryClient {
    @GetMapping("/ringtones/random")
    ApiResponse<Object> getRandomRingtone(@RequestParam("genre") String genre);
}

class LibraryClientFallback implements LibraryClient {
    @Override
    public ApiResponse<Object> getRandomRingtone(String genre) {
        // Fallback tối hậu: trả về 1 file âm thanh rỗng hoặc mặc định nếu cả library service cũng chết
        return ApiResponse.error("LIBRARY_UNAVAILABLE", "Community library is down");
    }
}
