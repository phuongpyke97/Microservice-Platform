package com.platform.crbtcampaign.client;

import com.platform.common.core.response.ApiResponse;
import com.platform.crbtcampaign.client.fallback.FileServiceClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "file-service", fallbackFactory = FileServiceClientFallback.class)
public interface FileServiceClient {

    @PostMapping(value = "/api/files/internal/upload-audio",
                 consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    ApiResponse<String> uploadAudio(@RequestBody byte[] bytes,
                                    @RequestParam(defaultValue = "media-audio") String bucket,
                                    @RequestParam(value = "prefix", required = false) String prefix);

    @org.springframework.web.bind.annotation.DeleteMapping("/api/files/internal/delete-file")
    ApiResponse<Void> deleteFileByUrl(@RequestParam("url") String url);
}
