package com.platform.common.security.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.rmq.RmqExchanges;
import com.platform.common.rmq.RmqRoutingKeys;
import com.platform.common.rmq.event.AuditLogEvent;
import com.platform.common.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Aspect for automatically publishing audit log events to RabbitMQ for controller methods.
 * Excludes audit-log-service controllers to prevent recursive audit logging.
 */
@Aspect
@Component
@Order(1)  // Run before other aspects like transaction management
@RequiredArgsConstructor
@Slf4j
public class AutoAuditLogAspect {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private static final String AUDIT_LOG_SERVICE_CONTROLLER = "com.platform.auditlogservice.controller";

    @Around("execution(* com.platform..controller..*(..)) && !execution(* " + AUDIT_LOG_SERVICE_CONTROLLER + "..*(..))")
    public Object auditLogAround(ProceedingJoinPoint joinPoint) throws Throwable {
        // Only proceed if we are in a web request context
        HttpServletRequest request = getHttpRequest();
        if (request == null) {
            return joinPoint.proceed();
        }

        long startTime = System.currentTimeMillis();
        Throwable exception = null;
        Object result;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            exception = ex;
            throw ex;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            try {
                AuditLogEvent auditEvent = buildAuditEvent(joinPoint, request, exception, duration);
                if (auditEvent != null) {
                    rabbitTemplate.convertAndSend(
                        RmqExchanges.AUDIT_EVENTS,
                        RmqRoutingKeys.AUDIT_LOG,
                        auditEvent
                    );
                    log.debug("Published audit event: {}", auditEvent);
                }
            } catch (Exception e) {
                // We only log the error but don't rethrow to avoid breaking the main business flow
                log.error("Failed to publish audit event: {}", e.getMessage());
            }
        }
    }

    private HttpServletRequest getHttpRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) requestAttributes).getRequest();
        }
        return null;
    }

    private AuditLogEvent buildAuditEvent(ProceedingJoinPoint joinPoint, HttpServletRequest request, Throwable exception, long duration) throws JsonProcessingException {
        // Derived from HTTP method + request URI
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String action = method + " " + uri;

        String status = (exception == null) ? "SUCCESS" : "FAILED";
        String sourceIp = extractSourceIp(request);
        Long userId = SecurityUtils.getCurrentUserId();
        String metadataJson = buildMetadataJson(joinPoint, request, exception, duration);
        long timestamp = Instant.now().toEpochMilli();

        return new AuditLogEvent(userId, action, sourceIp, status, metadataJson, timestamp);
    }

    private String extractSourceIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    private String buildMetadataJson(ProceedingJoinPoint joinPoint, HttpServletRequest request, Throwable exception, long duration) throws JsonProcessingException {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("controller", joinPoint.getTarget().getClass().getSimpleName());
        metadata.put("method", joinPoint.getSignature().getName());
        metadata.put("duration_ms", duration);
        metadata.put("request_uri", request.getRequestURI());

        Object tokenUsage = request.getAttribute("lyria_token_usage");
        if (tokenUsage != null) {
            metadata.put("lyria_token_usage", tokenUsage);
        }

        if (exception != null) {
            metadata.put("exception", exception.getClass().getName());
            metadata.put("message", exception.getMessage());
        }

        return objectMapper.writeValueAsString(metadata);
    }
}