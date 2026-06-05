package com.platform.crbtcampaign.client.dto;

import java.time.Instant;

public record DiyJobResponse(
    Long id,
    String prompt,
    String voiceId,
    String status,
    String resultUrl,
    String errorMessage,
    Instant createdAt
) {}
