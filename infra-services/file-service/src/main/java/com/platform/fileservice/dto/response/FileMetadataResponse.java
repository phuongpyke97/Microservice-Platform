package com.platform.fileservice.dto.response;

import com.platform.fileservice.entity.FileStatus;

public record FileMetadataResponse(
        Long id,
        Long userId,
        String originalName,
        String storedKey,
        String bucket,
        String contentType,
        long sizeBytes,
        FileStatus status
) {
}
