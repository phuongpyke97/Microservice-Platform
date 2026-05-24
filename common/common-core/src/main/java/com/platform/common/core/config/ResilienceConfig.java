package com.platform.common.core.config;

import feign.codec.ErrorDecoder;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResilienceConfig {

    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return new FeignClientErrorDecoder();
    }

    @Bean
    public CircuitBreakerConfigCustomizer defaultCircuitBreakerCustomizer() {
        return CircuitBreakerConfigCustomizer.of("default", builder -> builder
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(10)));
    }
}
