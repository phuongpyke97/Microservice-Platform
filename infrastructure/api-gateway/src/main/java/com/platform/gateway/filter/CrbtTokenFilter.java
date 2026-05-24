package com.platform.gateway.filter;

import com.platform.gateway.config.SecurityProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Verifies a CRBT subscriber token with the telco via shared secret, then injects
 * X-User-Id / X-MSISDN / X-Subscription-Type headers. Runs only when the request
 * carries an X-CRBT-Token header (no Authorization Bearer).
 */
@Component
public class CrbtTokenFilter implements GlobalFilter, Ordered {

    private static final String CRBT_TOKEN_HEADER = "X-CRBT-Token";

    private final SecurityProperties props;
    private final WebClient webClient;

    public CrbtTokenFilter(SecurityProperties props, WebClient.Builder builder) {
        this.props = props;
        this.webClient = builder.build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String crbtToken = exchange.getRequest().getHeaders().getFirst(CRBT_TOKEN_HEADER);
        if (crbtToken == null || crbtToken.isBlank()) {
            return chain.filter(exchange);
        }

        return webClient.post()
                .uri(props.crbt().verifyEndpoint())
                .header("X-Shared-Secret", props.crbt().sharedSecret())
                .bodyValue(Map.of("token", crbtToken))
                .retrieve()
                .bodyToMono(CrbtVerifyResponse.class)
                .flatMap(verified -> {
                    if (verified == null || !verified.valid()) {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }
                    ServerWebExchange mutated = exchange.mutate().request(r -> r
                            .header("X-User-Id", String.valueOf(verified.userId()))
                            .header("X-MSISDN", verified.msisdn() == null ? "" : verified.msisdn())
                            .header("X-Subscription-Type",
                                    verified.subscriptionType() == null ? "" : verified.subscriptionType())
                            .header("X-User-Roles", "USER")
                            .header(CRBT_TOKEN_HEADER, "")
                    ).build();
                    return chain.filter(mutated);
                })
                .onErrorResume(err -> {
                    exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                    return exchange.getResponse().setComplete();
                });
    }

    @Override
    public int getOrder() {
        return -150;
    }

    public record CrbtVerifyResponse(boolean valid, Long userId, String msisdn, String subscriptionType) {
    }
}
