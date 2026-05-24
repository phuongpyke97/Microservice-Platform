package com.platform.gateway.filter;

import com.platform.gateway.config.SecurityProperties;
import com.platform.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Handles JWT admin auth. Runs only when the request carries a Bearer token.
 * Gateway does NOT process CRBT tokens here — handled by CrbtTokenFilter.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SecurityProperties props;

    public JwtAuthFilter(SecurityProperties props) {
        this.props = props;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return chain.filter(exchange);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        if (!JwtUtil.isValid(token, props.jwt().secret())) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        Claims claims = JwtUtil.parse(token, props.jwt().secret());
        List<String> roles = JwtUtil.getRoles(claims);

        ServerWebExchange mutated = exchange.mutate().request(r -> r
                .header("X-User-Id", String.valueOf(claims.getSubject()))
                .header("X-User-Email", claims.get("email", String.class) != null
                        ? claims.get("email", String.class) : "")
                .header("X-User-Roles", String.join(",", roles))
                .header(HttpHeaders.AUTHORIZATION, "")
        ).build();

        return chain.filter(mutated);
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
