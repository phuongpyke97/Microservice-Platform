package com.platform.common.security;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class FeignCorrelationInterceptor implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FeignCorrelationInterceptor.class);

    @Override
    public void apply(RequestTemplate template) {
        String correlationId = MDC.get(MDCFilter.MDC_TRACE_ID);
        if (correlationId != null) {
            template.header(MDCFilter.CORRELATION_ID_HEADER, correlationId);
        }

        log.info("[FEIGN-INTERCEPTOR] Running apply for request URL={}", template.url());

        // Propagate security headers
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String userId = request.getHeader("X-User-Id");
            String roles = request.getHeader("X-User-Roles");
            String msisdn = request.getHeader("X-MSISDN");
            String email = request.getHeader("X-User-Email");

            log.info("[FEIGN-INTERCEPTOR] Propagating from request headers: X-User-Id={}, X-User-Roles={}, X-MSISDN={}", userId, roles, msisdn);

            if (userId != null) template.header("X-User-Id", userId);
            if (roles != null) template.header("X-User-Roles", roles);
            if (msisdn != null) template.header("X-MSISDN", msisdn);
            if (email != null) template.header("X-User-Email", email);
        } else {
            // Fallback to SecurityUtils context
            Long currentUserId = SecurityUtils.getCurrentUserId();
            List<String> roles = SecurityUtils.getCurrentUserRoles();
            String msisdn = SecurityUtils.getCurrentMsisdn();

            log.info("[FEIGN-INTERCEPTOR] Propagating from SecurityUtils: userId={}, roles={}, msisdn={}", currentUserId, roles, msisdn);

            if (currentUserId != null) {
                template.header("X-User-Id", String.valueOf(currentUserId));
            }
            if (roles != null && !roles.isEmpty()) {
                template.header("X-User-Roles", String.join(",", roles));
            }
            if (msisdn != null) {
                template.header("X-MSISDN", msisdn);
            }
        }
    }
}
