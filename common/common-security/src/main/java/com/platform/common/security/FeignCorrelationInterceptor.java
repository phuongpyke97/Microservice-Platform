package com.platform.common.security;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class FeignCorrelationInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String correlationId = MDC.get(MDCFilter.MDC_TRACE_ID);
        if (correlationId != null) {
            template.header(MDCFilter.CORRELATION_ID_HEADER, correlationId);
        }
    }
}
