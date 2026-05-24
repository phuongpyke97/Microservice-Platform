package com.platform.fileservice.dto.response;

public record PresignedUrlResponse(String objectKey, String url, long expiresInSeconds) {
}
