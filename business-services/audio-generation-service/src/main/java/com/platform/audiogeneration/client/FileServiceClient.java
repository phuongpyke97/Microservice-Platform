package com.platform.audiogeneration.client;

import com.platform.common.core.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(name = "file-service", fallback = FileServiceClientFallback.class)
public interface FileServiceClient {
    @PostMapping(value = "/api/files/upload", consumes = "multipart/form-data")
    ApiResponse<String> uploadFile(@RequestPart("file") MultipartFile file, @RequestParam("bucket") String bucket);
}

class FileServiceClientFallback implements FileServiceClient {
    @Override
    public ApiResponse<String> uploadFile(MultipartFile file, String bucket) {
        return ApiResponse.error("FILE_SERVICE_UNAVAILABLE", "File service is currently down");
    }
}
