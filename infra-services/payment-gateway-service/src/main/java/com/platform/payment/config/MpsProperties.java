package com.platform.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mps")
public record MpsProperties(String endpoint, String apiKey, long timeoutMs) {
}
