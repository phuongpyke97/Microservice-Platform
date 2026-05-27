package com.platform.fileservice.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    @Bean
    @org.springframework.context.annotation.Primary
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    @Bean(name = "publicMinioClient")
    public MinioClient publicMinioClient(MinioProperties properties) {
        String endpoint = (properties.externalEndpoint() != null && !properties.externalEndpoint().isBlank())
                ? properties.externalEndpoint()
                : properties.endpoint();
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }
}
