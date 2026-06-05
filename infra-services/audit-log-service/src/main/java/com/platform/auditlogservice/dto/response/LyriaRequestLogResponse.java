package com.platform.auditlogservice.dto.response;

import java.time.Instant;

public record LyriaRequestLogResponse(
    Long id,
    Long userId,
    String msisdn,
    String model,
    int promptTokens,
    int candidateTokens,
    int totalTokens,
    int latencyMs,
    String status,
    String errorMessage,
    Instant createdAt
) {}
