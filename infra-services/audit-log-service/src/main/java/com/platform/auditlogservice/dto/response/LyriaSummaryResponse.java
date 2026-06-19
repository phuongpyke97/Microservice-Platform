package com.platform.auditlogservice.dto.response;

import java.math.BigDecimal;

public record LyriaSummaryResponse(
    long totalTokens,
    int totalSongs,
    double avgTokensPerSong,
    BigDecimal estimatedCostUsd
) {}
