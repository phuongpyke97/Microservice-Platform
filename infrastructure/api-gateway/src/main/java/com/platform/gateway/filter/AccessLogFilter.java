package com.platform.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AccessLogFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long start = System.currentTimeMillis();
        // Capture traceId synchronously — MDC is thread-local and unavailable in doFinally's scheduler thread
        String traceId = MDC.get(TraceFilter.CORRELATION_ID_HEADER) != null
                ? MDC.get(TraceFilter.CORRELATION_ID_HEADER)
                : MDC.get("traceId") != null ? MDC.get("traceId") : "-";
        String client = request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";

        log.info("[GATEWAY] --> {} {} | client={} | traceId={}",
                request.getMethod(), request.getURI().getRawPath(), client, traceId);

        return chain.filter(exchange).doFinally(signal -> {
            long duration = System.currentTimeMillis() - start;
            HttpStatus status = exchange.getResponse().getStatusCode() instanceof HttpStatus hs ? hs : null;
            String statusStr = status != null ? status.toString() : "CANCELLED";
            log.info("[GATEWAY] <-- {} {} | status={} | {}ms | traceId={}",
                    request.getMethod(), request.getURI().getRawPath(), statusStr, duration, traceId);
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
