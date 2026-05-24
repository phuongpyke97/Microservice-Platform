package com.platform.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsValuesFromHeaderAuthenticationToken() {
        HeaderAuthenticationToken token = new HeaderAuthenticationToken(
                99L,
                "user@example.com",
                "959123456789",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(token);

        assertThat(SecurityUtils.getCurrentUserId()).isEqualTo(99L);
        assertThat(SecurityUtils.getCurrentUserEmail()).isEqualTo("user@example.com");
        assertThat(SecurityUtils.getCurrentMsisdn()).isEqualTo("959123456789");
        assertThat(SecurityUtils.getCurrentUserRoles()).containsExactly("ADMIN", "USER");
    }

    @Test
    void returnsNullOrEmptyWhenNoAuthenticationPresent() {
        assertThat(SecurityUtils.getCurrentUserId()).isNull();
        assertThat(SecurityUtils.getCurrentUserEmail()).isNull();
        assertThat(SecurityUtils.getCurrentMsisdn()).isNull();
        assertThat(SecurityUtils.getCurrentUserRoles()).isEmpty();
    }
}
