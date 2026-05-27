package com.platform.fileservice.dto.response;

public record PresignedUrlResponse(Long fileId, String objectKey, String url, long expiresInSeconds) {
}

