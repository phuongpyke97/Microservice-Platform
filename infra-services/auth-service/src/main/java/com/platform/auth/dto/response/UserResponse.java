package com.platform.auth.dto.response;

public record UserResponse(
        Long id,
        String msisdn,
        String email,
        String status,
        Long createdAt
) {}
