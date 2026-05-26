package com.platform.fileservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minio")
public record MinioProperties(
        String endpoint,
        String externalEndpoint,
        String accessKey,
        String secretKey,
        String bucketTemp,
        String bucketAudio,
        String bucketImage
) {
    public String publicEndpoint() {
        return (externalEndpoint != null && !externalEndpoint.isBlank()) ? externalEndpoint : endpoint;
    }
}
