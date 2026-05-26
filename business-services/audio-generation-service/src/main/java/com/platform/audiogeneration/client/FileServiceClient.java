package com.platform.audiogeneration.client;

import com.platform.common.core.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(name = "file-service", fallback = FileServiceClientFallback.class)
public interface FileServiceClient {
    @PostMapping(value = "/api/files/upload", consumes = "multipart/form-data")
    ApiResponse<String> uploadFile(@RequestPart("file") MultipartFile file, @RequestParam("bucket") String bucket);

    @PostMapping(value = "/api/files/internal/upload-audio", consumes = "application/octet-stream")
    ApiResponse<String> uploadAudioBytes(byte[] audioBytes, @RequestParam("bucket") String bucket);

    @GetMapping("/api/files/{fileId}/presigned/download")
    ApiResponse<java.util.Map<String, Object>> getDownloadUrl(@PathVariable("fileId") Long fileId);
}

class FileServiceClientFallback implements FileServiceClient {
    @Override
    public ApiResponse<String> uploadFile(MultipartFile file, String bucket) {
        return ApiResponse.error("FILE_SERVICE_UNAVAILABLE", "File service is currently down");
    }

    @Override
    public ApiResponse<String> uploadAudioBytes(byte[] audioBytes, String bucket) {
        return ApiResponse.error("FILE_SERVICE_UNAVAILABLE", "File service is currently down");
    }

    @Override
    public ApiResponse<java.util.Map<String, Object>> getDownloadUrl(Long fileId) {
        return ApiResponse.error("FILE_SERVICE_UNAVAILABLE", "File service is currently down");
    }
}
