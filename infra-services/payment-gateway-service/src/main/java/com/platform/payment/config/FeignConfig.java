package com.platform.payment.config;

import feign.RequestInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MpsProperties.class)
public class FeignConfig {

    @Bean
    public RequestInterceptor mpsAuthInterceptor(MpsProperties properties) {
        return template -> {
            if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
                template.header("X-API-Key", properties.apiKey());
            }
        };
    }
}
