package com.platform.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads X-User-* / X-MSISDN headers injected by the API Gateway and populates the SecurityContext.
 * Internal services never re-validate the JWT or CRBT token; the Gateway already did.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_EMAIL = "X-User-Email";
    private static final String HEADER_USER_ROLES = "X-User-Roles";
    private static final String HEADER_MSISDN = "X-MSISDN";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userIdHeader = request.getHeader(HEADER_USER_ID);
        log.info("[JWT-FILTER] Incoming request URI={}, X-User-Id={}, X-User-Roles={}", 
            request.getRequestURI(), userIdHeader, request.getHeader(HEADER_USER_ROLES));
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            Long userId = Long.parseLong(userIdHeader.trim());
            String email = request.getHeader(HEADER_USER_EMAIL);
            String msisdn = request.getHeader(HEADER_MSISDN);
            String rolesHeader = request.getHeader(HEADER_USER_ROLES);

            List<SimpleGrantedAuthority> authorities = Arrays
                    .stream((rolesHeader == null || rolesHeader.isBlank() ? "USER" : rolesHeader).split(","))
                    .map(String::trim)
                    .filter(role -> !role.isBlank())
                    .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            SecurityContextHolder.getContext().setAuthentication(
                    new HeaderAuthenticationToken(userId, email, msisdn, authorities));
        }
        filterChain.doFilter(request, response);
    }
}
