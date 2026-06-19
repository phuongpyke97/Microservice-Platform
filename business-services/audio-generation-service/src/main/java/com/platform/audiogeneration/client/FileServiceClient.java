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
    ApiResponse<String> uploadAudioBytes(
        @org.springframework.web.bind.annotation.RequestBody byte[] audioBytes,
        @RequestParam("bucket") String bucket,
        @RequestParam(value = "prefix", required = false) String prefix
    );

    @GetMapping("/api/files/{fileId}/presigned/download")
    ApiResponse<java.util.Map<String, Object>> getDownloadUrl(@PathVariable("fileId") Long fileId);

    @GetMapping("/api/files/{fileId}/internal/presigned/download")
    ApiResponse<java.util.Map<String, Object>> getInternalDownloadUrl(@PathVariable("fileId") Long fileId);

    @GetMapping("/api/files/{fileId}/internal/download")
    feign.Response downloadFile(@PathVariable("fileId") Long fileId);

    @GetMapping("/api/files/internal/download-by-url")
    feign.Response downloadFileByUrl(@RequestParam("url") String url);

    @PostMapping("/api/files/{fileId}/confirm")
    ApiResponse<java.util.Map<String, Object>> confirmFile(
        @PathVariable("fileId") Long fileId,
        @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, String> request
    );

    @org.springframework.web.bind.annotation.DeleteMapping("/api/files/internal/delete-file")
    ApiResponse<Void> deleteFileByUrl(@RequestParam("url") String url);
}

class FileServiceClientFallback implements FileServiceClient {
    @Override
    public ApiResponse<String> uploadFile(MultipartFile file, String bucket) {
        return ApiResponse.error("FILE_SERVICE_UNAVAILABLE", "File service is currently down");
    }

    @Override
    public ApiResponse<String> uploadAudioBytes(byte[] audioBytes, String bucket, String prefix) {
        return ApiResponse.error("FILE_SERVICE_UNAVAILABLE", "File service is currently down");
    }

    @Override
    public ApiResponse<java.util.Map<String, Object>> getDownloadUrl(Long fileId) {
        return ApiResponse.error("FILE_SERVICE_UNAVAILABLE", "File service is currently down");
    }

    @Override
    public ApiResponse<java.util.Map<String, Object>> getInternalDownloadUrl(Long fileId) {
        return ApiResponse.error("FILE_SERVICE_UNAVAILABLE", "File service is currently down");
    }

    @Override
    public feign.Response downloadFile(Long fileId) {
        throw new RuntimeException("File service is currently down");
    }

    @Override
    public feign.Response downloadFileByUrl(String url) {
        throw new RuntimeException("File service is currently down");
    }

    @Override
    public ApiResponse<java.util.Map<String, Object>> confirmFile(Long fileId, java.util.Map<String, String> request) {
        return ApiResponse.error("FILE_SERVICE_UNAVAILABLE", "File service is currently down");
    }

    @Override
    public ApiResponse<Void> deleteFileByUrl(String url) {
        return ApiResponse.error("FILE_SERVICE_UNAVAILABLE", "File service is currently down");
    }
}
