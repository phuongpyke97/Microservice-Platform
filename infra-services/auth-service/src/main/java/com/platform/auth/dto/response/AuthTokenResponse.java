package com.platform.auth.dto.response;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType
) {
    public static AuthTokenResponse of(String access, String refresh, long expiresIn) {
        return new AuthTokenResponse(access, refresh, expiresIn, "Bearer");
    }
}
