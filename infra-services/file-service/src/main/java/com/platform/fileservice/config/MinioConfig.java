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
        okhttp3.OkHttpClient httpClient = new okhttp3.OkHttpClient.Builder()
                .proxy(java.net.Proxy.NO_PROXY)
                .build();
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .region("us-east-1") // Bypass auto-detecting bucket location (no network call)
                .credentials(properties.accessKey(), properties.secretKey())
                .httpClient(httpClient)
                .build();
    }

    @Bean(name = "publicMinioClient")
    public MinioClient publicMinioClient(MinioProperties properties) {
        String endpoint = (properties.externalEndpoint() != null && !properties.externalEndpoint().isBlank())
                ? properties.externalEndpoint()
                : properties.endpoint();
        okhttp3.OkHttpClient httpClient = new okhttp3.OkHttpClient.Builder()
                .proxy(java.net.Proxy.NO_PROXY)
                .build();
        return MinioClient.builder()
                .endpoint(endpoint)
                .region("us-east-1") // Bypass auto-detecting bucket location (no network call)
                .credentials(properties.accessKey(), properties.secretKey())
                .httpClient(httpClient)
                .build();
    }
}
