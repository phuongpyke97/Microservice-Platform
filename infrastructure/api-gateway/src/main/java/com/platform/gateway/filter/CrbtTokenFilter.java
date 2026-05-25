package com.platform.gateway.filter;

import com.platform.gateway.config.SecurityProperties;
import com.platform.gateway.dto.CrbtProvisionRequest;
import com.platform.gateway.dto.CrbtProvisionResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Verifies a CRBT subscriber token via HMAC-SHA shared secret (local, no round-trip),
 * then calls auth-service to lazy-provision the platform account,
 * and injects X-User-Id (platform) / X-MSISDN / X-User-Roles / X-Subscription-Type.
 *
 * Order: -150 — runs after JwtAuthFilter (-200). Skips requests without X-CRBT-Token.
 *
 * CRBT JWT payload: { "phone": "...", "status": 1, "id": <telco_id>, "sub": "...", "loginType": ... }
 * status == 1 → active subscriber.
 */
@Component
public class CrbtTokenFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CrbtTokenFilter.class);

    private static final String CRBT_TOKEN_HEADER = "X-CRBT-Token";
    private static final String CORRELATION_HEADER = "X-Correlation-ID";

    private final SecurityProperties props;
    private final WebClient webClient;

    public CrbtTokenFilter(SecurityProperties props, WebClient.Builder webClientBuilder) {
        this.props = props;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String crbtToken = exchange.getRequest().getHeaders().getFirst(CRBT_TOKEN_HEADER);
        if (crbtToken == null || crbtToken.isBlank()) {
            return chain.filter(exchange);
        }

        String traceId = exchange.getRequest().getHeaders().getFirst(CORRELATION_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        final String finalTraceId = traceId;

        // Step 1: Verify CRBT JWT signature locally with CRBT_SHARED_SECRET
        Claims claims;
        try {
            byte[] secretBytes = props.crbt().sharedSecret().getBytes(StandardCharsets.UTF_8);
            claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(secretBytes))
                    .build()
                    .parseSignedClaims(crbtToken)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("[CRBT] traceId={} Token expired", finalTraceId);
            return writeError(exchange, HttpStatus.UNAUTHORIZED,
                    "AUTH_CRBT_TOKEN_EXPIRED", "CRBT token has expired");
        } catch (Exception e) {
            log.warn("[CRBT] traceId={} Token invalid: {}", finalTraceId, e.getMessage());
            return writeError(exchange, HttpStatus.UNAUTHORIZED,
                    "AUTH_CRBT_TOKEN_INVALID", "CRBT token is invalid or malformed");
        }

        // Step 2: Check subscriber active status (status must be 1)
        Integer status = claims.get("status", Integer.class);
        if (status == null || status != 1) {
            log.warn("[CRBT] traceId={} Subscriber not active status={}", finalTraceId, status);
            return writeError(exchange, HttpStatus.FORBIDDEN,
                    "AUTH_CRBT_SUBSCRIBER_INACTIVE", "CRBT subscriber is not active");
        }

        // Step 3: Extract MSISDN (phone claim preferred, fallback to sub)
        String phone = claims.get("phone", String.class);
        String msisdn = (phone != null && !phone.isBlank()) ? phone : claims.getSubject();
        Integer loginType = claims.get("loginType", Integer.class);

        if (msisdn == null || msisdn.isBlank()) {
            log.warn("[CRBT] traceId={} Missing MSISDN in token claims", finalTraceId);
            return writeError(exchange, HttpStatus.BAD_REQUEST,
                    "AUTH_CRBT_TOKEN_MISSING_MSISDN", "CRBT token missing phone/subject claim");
        }

        final String finalMsisdn = msisdn;
        final String subscriptionType = loginType != null ? String.valueOf(loginType) : "";

        log.info("[CRBT] traceId={} JWT valid, provisioning msisdn={}", finalTraceId, mask(finalMsisdn));

        // Step 4: Call auth-service to get-or-create platform user by MSISDN
        return webClient.post()
                .uri("lb://auth-service/internal/crbt/provision")
                .header(CORRELATION_HEADER, finalTraceId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CrbtProvisionRequest(finalMsisdn))
                .retrieve()
                .bodyToMono(CrbtProvisionResponse.class)
                .flatMap(provision -> {
                    log.info("[CRBT] traceId={} Provision ok userId={} msisdn={}",
                            finalTraceId, provision.userId(), mask(finalMsisdn));

                    ServerWebExchange mutated = exchange.mutate().request(r -> r
                            .header("X-User-Id", String.valueOf(provision.userId()))
                            .header("X-MSISDN", finalMsisdn)
                            .header("X-Subscription-Type", subscriptionType)
                            .header("X-User-Roles", String.join(",", provision.roles()))
                            .header(CRBT_TOKEN_HEADER, "")
                    ).build();

                    return chain.filter(mutated);
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("[CRBT] traceId={} auth-service provision failed status={} body={}",
                            finalTraceId, e.getStatusCode(), e.getResponseBodyAsString());
                    return writeError(exchange, HttpStatus.UNAUTHORIZED,
                            "AUTH_CRBT_PROVISION_FAILED", "Failed to provision CRBT subscriber");
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("[CRBT] traceId={} Unexpected error msisdn={}: {}",
                            finalTraceId, mask(finalMsisdn), e.getMessage(), e);
                    return writeError(exchange, HttpStatus.INTERNAL_SERVER_ERROR,
                            "AUTH_CRBT_INTERNAL_ERROR", "Internal error processing CRBT token");
                });
    }

    @Override
    public int getOrder() {
        return -150;
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"success\":false,\"errorCode\":\"%s\",\"message\":\"%s\",\"timestamp\":%d}",
                code, message, System.currentTimeMillis());
        DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String mask(String msisdn) {
        if (msisdn == null || msisdn.length() <= 4) return "***";
        return msisdn.substring(0, 3) + "***" + msisdn.substring(msisdn.length() - 2);
    }
}
