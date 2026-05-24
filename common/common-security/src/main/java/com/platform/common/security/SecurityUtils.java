package com.platform.common.security;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long getCurrentUserId() {
        return currentToken() != null ? currentToken().getUserId() : null;
    }

    public static String getCurrentUserEmail() {
        return currentToken() != null ? currentToken().getEmail() : null;
    }

    public static String getCurrentMsisdn() {
        return currentToken() != null ? currentToken().getMsisdn() : null;
    }

    public static List<String> getCurrentUserRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return List.of();
        }
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .toList();
    }

    private static HeaderAuthenticationToken currentToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof HeaderAuthenticationToken token ? token : null;
    }
}
