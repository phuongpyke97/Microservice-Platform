package com.platform.gateway.filter;

import com.platform.gateway.config.SecurityProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;

/**
 * Verifies a CRBT subscriber token via its JWT signature using the shared secret,
 * then injects X-User-Id / X-MSISDN / X-Subscription-Type headers.
 * Runs only when the request carries an X-CRBT-Token header.
 */
@Component
public class CrbtTokenFilter implements GlobalFilter, Ordered {

    private static final String CRBT_TOKEN_HEADER = "X-CRBT-Token";

    private final SecurityProperties props;

    public CrbtTokenFilter(SecurityProperties props) {
        this.props = props;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String crbtToken = exchange.getRequest().getHeaders().getFirst(CRBT_TOKEN_HEADER);
        if (crbtToken == null || crbtToken.isBlank()) {
            return chain.filter(exchange);
        }

        try {
            byte[] secretBytes = props.crbt().sharedSecret().getBytes(StandardCharsets.UTF_8);
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(secretBytes))
                    .build()
                    .parseClaimsJws(crbtToken)
                    .getBody();

            // Extract based on payload: { "phone", "status": 1, "id": 24, "sub", "loginType" }
            Integer status = claims.get("status", Integer.class);
            if (status == null || status != 1) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            String msisdn = claims.get("phone", String.class);
            if (msisdn == null) {
                msisdn = claims.getSubject();
            }
            Integer userId = claims.get("id", Integer.class);
            Integer loginType = claims.get("loginType", Integer.class);

            ServerWebExchange mutated = exchange.mutate().request(r -> r
                    .header("X-User-Id", userId != null ? String.valueOf(userId) : "")
                    .header("X-MSISDN", msisdn != null ? msisdn : "")
                    .header("X-Subscription-Type", loginType != null ? String.valueOf(loginType) : "")
                    .header("X-User-Roles", "USER")
                    .header(CRBT_TOKEN_HEADER, "")
            ).build();

            return chain.filter(mutated);

        } catch (Exception err) {
            // Catches ExpiredJwtException, SignatureException, MalformedJwtException, etc.
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -150;
    }
}
