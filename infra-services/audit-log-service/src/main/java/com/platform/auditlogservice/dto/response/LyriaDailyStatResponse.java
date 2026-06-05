package com.platform.auditlogservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LyriaDailyStatResponse(
    LocalDate statDate,
    int totalRequests,
    int failedRequests,
    long totalPromptTokens,
    long totalCandidateTokens,
    long totalTokens,
    int avgLatencyMs,
    BigDecimal estimatedCostUsd
) {}
