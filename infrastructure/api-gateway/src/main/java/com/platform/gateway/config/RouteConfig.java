package com.platform.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // API Routes (Rewrite to match exact Controller paths)
                .route("auth-service", r -> r.path("/api/auth/**").uri("lb://auth-service"))
                .route("notification-service", r -> r.path("/api/notifications/**").uri("lb://notification-service"))
                .route("audit-log-service", r -> r.path("/api/audit-logs/**").filters(f -> f.rewritePath("/api/audit-logs/(?<segment>.*)", "/audit/${segment}")).uri("lb://audit-log-service"))
                .route("file-service", r -> r.path("/api/files/**").uri("lb://file-service"))
                .route("payment-gateway-service", r -> r.path("/api/payments/**").uri("lb://payment-gateway-service"))
                .route("credit-wallet-service", r -> r.path("/api/wallet/**").uri("lb://credit-wallet-service"))
                .route("crbt-campaign-service", r -> r.path("/api/campaigns/**").filters(f -> f.rewritePath("/api/campaigns/(?<segment>.*)", "/campaigns/${segment}")).uri("lb://crbt-campaign-service"))
                .route("crbt-community-library", r -> r.path("/api/library/**").filters(f -> f.rewritePath("/api/library/(?<segment>.*)", "/library/${segment}")).uri("lb://crbt-community-library"))
                .route("audio-generation-service", r -> r.path("/api/audio/**").filters(f -> f.rewritePath("/api/audio/(?<segment>.*)", "/audio-jobs/${segment}")).uri("lb://audio-generation-service"))
                .route("crbt-credit-transaction-service", r -> r.path("/api/credits/**").filters(f -> f.rewritePath("/api/credits/(?<segment>.*)", "/credit-transactions/${segment}")).uri("lb://crbt-credit-transaction-service"))
                .route("crbt-core-adapter", r -> r.path("/api/core-adapter/**").filters(f -> f.rewritePath("/api/core-adapter/(?<segment>.*)", "/ringtone-assignments/${segment}")).uri("lb://crbt-core-adapter"))

                // Swagger UI & Direct Service Routes
                // These handle both /v3/api-docs AND calls made directly from Swagger UI
                .route("auth-swagger", r -> r.path("/auth-service/**").filters(f -> f.rewritePath("/auth-service/(?<segment>.*)", "/${segment}")).uri("lb://auth-service"))
                .route("notification-swagger", r -> r.path("/notification-service/**").filters(f -> f.rewritePath("/notification-service/(?<segment>.*)", "/${segment}")).uri("lb://notification-service"))
                .route("audit-log-swagger", r -> r.path("/audit-log-service/**").filters(f -> f.rewritePath("/audit-log-service/(?<segment>.*)", "/${segment}")).uri("lb://audit-log-service"))
                .route("file-swagger", r -> r.path("/file-service/**").filters(f -> f.rewritePath("/file-service/(?<segment>.*)", "/${segment}")).uri("lb://file-service"))
                .route("payment-swagger", r -> r.path("/payment-gateway-service/**").filters(f -> f.rewritePath("/payment-gateway-service/(?<segment>.*)", "/${segment}")).uri("lb://payment-gateway-service"))
                .route("wallet-swagger", r -> r.path("/credit-wallet-service/**").filters(f -> f.rewritePath("/credit-wallet-service/(?<segment>.*)", "/${segment}")).uri("lb://credit-wallet-service"))
                .route("campaign-swagger", r -> r.path("/crbt-campaign-service/**").filters(f -> f.rewritePath("/crbt-campaign-service/(?<segment>.*)", "/${segment}")).uri("lb://crbt-campaign-service"))
                .route("library-swagger", r -> r.path("/crbt-community-library/**").filters(f -> f.rewritePath("/crbt-community-library/(?<segment>.*)", "/${segment}")).uri("lb://crbt-community-library"))
                .route("audio-swagger", r -> r.path("/audio-generation-service/**").filters(f -> f.rewritePath("/audio-generation-service/(?<segment>.*)", "/${segment}")).uri("lb://audio-generation-service"))
                .route("credit-transaction-swagger", r -> r.path("/crbt-credit-transaction-service/**").filters(f -> f.rewritePath("/crbt-credit-transaction-service/(?<segment>.*)", "/${segment}")).uri("lb://crbt-credit-transaction-service"))
                .route("core-adapter-swagger", r -> r.path("/crbt-core-adapter/**").filters(f -> f.rewritePath("/crbt-core-adapter/(?<segment>.*)", "/${segment}")).uri("lb://crbt-core-adapter"))

                .build();
    }
}
