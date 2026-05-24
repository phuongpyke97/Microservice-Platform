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
                // API Routes
                .route("auth-service", r -> r.path("/api/auth/**").uri("lb://auth-service"))
                .route("notification-service", r -> r.path("/api/notifications/**").uri("lb://notification-service"))
                .route("audit-log-service", r -> r.path("/api/audit-logs/**").uri("lb://audit-log-service"))
                .route("file-service", r -> r.path("/api/files/**").uri("lb://file-service"))
                .route("payment-gateway-service", r -> r.path("/api/payments/**").uri("lb://payment-gateway-service"))
                .route("credit-wallet-service", r -> r.path("/api/wallet/**").uri("lb://credit-wallet-service"))
                .route("crbt-campaign-service", r -> r.path("/api/campaigns/**").uri("lb://crbt-campaign-service"))
                .route("crbt-community-library", r -> r.path("/api/library/**").uri("lb://crbt-community-library"))
                .route("audio-generation-service", r -> r.path("/api/audio/**").uri("lb://audio-generation-service"))
                .route("crbt-credit-transaction-service", r -> r.path("/api/credits/**").uri("lb://crbt-credit-transaction-service"))
                .route("crbt-core-adapter", r -> r.path("/api/core-adapter/**").uri("lb://crbt-core-adapter"))

                // Swagger UI Routes
                .route("auth-swagger", r -> r.path("/auth-service/v3/api-docs").filters(f -> f.rewritePath("/auth-service/(?<segment>.*)", "/${segment}")).uri("lb://auth-service"))
                .route("notification-swagger", r -> r.path("/notification-service/v3/api-docs").filters(f -> f.rewritePath("/notification-service/(?<segment>.*)", "/${segment}")).uri("lb://notification-service"))
                .route("audit-log-swagger", r -> r.path("/audit-log-service/v3/api-docs").filters(f -> f.rewritePath("/audit-log-service/(?<segment>.*)", "/${segment}")).uri("lb://audit-log-service"))
                .route("file-swagger", r -> r.path("/file-service/v3/api-docs").filters(f -> f.rewritePath("/file-service/(?<segment>.*)", "/${segment}")).uri("lb://file-service"))
                .route("payment-swagger", r -> r.path("/payment-gateway-service/v3/api-docs").filters(f -> f.rewritePath("/payment-gateway-service/(?<segment>.*)", "/${segment}")).uri("lb://payment-gateway-service"))
                .route("wallet-swagger", r -> r.path("/credit-wallet-service/v3/api-docs").filters(f -> f.rewritePath("/credit-wallet-service/(?<segment>.*)", "/${segment}")).uri("lb://credit-wallet-service"))
                .route("campaign-swagger", r -> r.path("/crbt-campaign-service/v3/api-docs").filters(f -> f.rewritePath("/crbt-campaign-service/(?<segment>.*)", "/${segment}")).uri("lb://crbt-campaign-service"))
                .route("library-swagger", r -> r.path("/crbt-community-library/v3/api-docs").filters(f -> f.rewritePath("/crbt-community-library/(?<segment>.*)", "/${segment}")).uri("lb://crbt-community-library"))
                .route("audio-swagger", r -> r.path("/audio-generation-service/v3/api-docs").filters(f -> f.rewritePath("/audio-generation-service/(?<segment>.*)", "/${segment}")).uri("lb://audio-generation-service"))
                .route("credit-transaction-swagger", r -> r.path("/crbt-credit-transaction-service/v3/api-docs").filters(f -> f.rewritePath("/crbt-credit-transaction-service/(?<segment>.*)", "/${segment}")).uri("lb://crbt-credit-transaction-service"))
                .route("core-adapter-swagger", r -> r.path("/crbt-core-adapter/v3/api-docs").filters(f -> f.rewritePath("/crbt-core-adapter/(?<segment>.*)", "/${segment}")).uri("lb://crbt-core-adapter"))

                .build();
    }
}
