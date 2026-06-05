package com.platform.auditlogservice.dto.response;

public record LyriaSummaryResponse(
    long totalTokens,
    int totalSongs,
    double avgTokensPerSong
) {}
