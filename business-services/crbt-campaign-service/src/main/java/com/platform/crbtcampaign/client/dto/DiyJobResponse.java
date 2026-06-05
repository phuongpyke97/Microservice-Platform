package com.platform.crbtcampaign.client.dto;

import java.time.Instant;

public record DiyJobResponse(
    Long id,
    String prompt,
    String voiceId,
    String status,
    String resultUrl,
    String errorMessage,
    Instant createdAt,
    String title,
    String msisdn
) {
    public DiyJobResponse(Long id, String prompt, String voiceId, String status, String resultUrl, String errorMessage, Instant createdAt) {
        this(id, prompt, voiceId, status, resultUrl, errorMessage, createdAt, null, null);
    }
}
