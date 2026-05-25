package com.platform.common.core.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class DebugLoggingAspect {

    @Around("execution(* com.platform..controller..*(..)) || execution(* com.platform..service..*(..))")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        Logger log = LoggerFactory.getLogger(joinPoint.getTarget().getClass());
        String method = joinPoint.getSignature().toShortString();
        long start = System.currentTimeMillis();

        if (log.isDebugEnabled()) {
            log.debug("Start {}", method);
        }

        try {
            Object result = joinPoint.proceed();
            if (log.isDebugEnabled()) {
                log.debug("End {} ({} ms)", method, System.currentTimeMillis() - start);
            }
            return result;
        } catch (Throwable ex) {
            log.debug("Error {} ({} ms): {}", method, System.currentTimeMillis() - start, ex.getMessage(), ex);
            throw ex;
        }
    }
}
