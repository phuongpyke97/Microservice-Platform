package com.platform.auth.util;

import com.platform.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(
                "this-is-a-test-secret-that-must-be-at-least-256-bits-long-padding",
                3_600_000L,
                86_400_000L
        );
        provider = new JwtTokenProvider(props);
    }

    @Test
    void generateAccessToken_valid() {
        String token = provider.generateAccessToken(1L, "test@example.com", List.of("ADMIN"));
        assertThat(provider.isValid(token)).isTrue();
    }

    @Test
    void generateRefreshToken_valid() {
        String token = provider.generateRefreshToken(1L, "test@example.com", List.of("USER"));
        assertThat(provider.isValid(token)).isTrue();
    }

    @Test
    void parse_returnsCorrectSubjectAndEmail() {
        String token = provider.generateAccessToken(42L, "user@example.com", List.of("USER"));
        Claims claims = provider.parse(token);
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
    }

    @Test
    void isValid_invalidToken_returnsFalse() {
        assertThat(provider.isValid("not.a.jwt")).isFalse();
    }

    @Test
    void isValid_tamperedToken_returnsFalse() {
        String token = provider.generateAccessToken(1L, "a@b.com", List.of("USER"));
        String tampered = token.substring(0, token.length() - 4) + "XXXX";
        assertThat(provider.isValid(tampered)).isFalse();
    }

    @Test
    void accessTokenTtlSeconds_returnsMillisDividedBy1000() {
        assertThat(provider.accessTokenTtlSeconds()).isEqualTo(3600L);
    }
}
