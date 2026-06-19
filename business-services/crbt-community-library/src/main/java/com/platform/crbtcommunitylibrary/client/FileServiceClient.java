package com.platform.crbtcommunitylibrary.client;

import com.platform.common.core.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "file-service")
public interface FileServiceClient {

    @GetMapping("/api/files/{fileId}")
    ApiResponse<FileMetadataResponse> getFileMetadata(@PathVariable("fileId") Long fileId);

    @PostMapping("/api/files/internal/copy-to-public")
    ApiResponse<String> copyToPublic(@RequestParam("fileId") Long fileId, @RequestParam("targetBucket") String targetBucket);

    record FileMetadataResponse(
        Long id,
        Long userId,
        String originalName,
        String storedKey,
        String bucket,
        String contentType,
        Long sizeBytes,
        String status
    ) {}
}
