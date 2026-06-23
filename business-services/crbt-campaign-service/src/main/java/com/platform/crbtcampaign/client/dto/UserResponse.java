package com.platform.crbtcampaign.client.dto;

public record UserResponse(
        Long id,
        String msisdn,
        String email,
        String status,
        Long createdAt
) {}
