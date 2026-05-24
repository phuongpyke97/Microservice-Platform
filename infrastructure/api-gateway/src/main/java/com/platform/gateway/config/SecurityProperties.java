package com.platform.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security")
public record SecurityProperties(Jwt jwt, Crbt crbt, RateLimit rateLimit) {

    public record Jwt(String secret) {
    }

    public record Crbt(String sharedSecret, String verifyEndpoint) {
    }

    public record RateLimit(int replenishRate, int burstCapacity) {
    }
}
