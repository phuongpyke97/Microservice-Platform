package com.platform.crbtcampaign.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record MyLibraryItemResponse(
    String id,
    String title,
    String source, // "AI" or "DIY"
    List<String> tags, // e.g. ["pop", "happy", "piano"] for AI, or ["DIY"] for DIY
    String audioUrl,
    Instant createdAt,
    String msisdn,
    BigDecimal estimatedCostUsd
) {
    public MyLibraryItemResponse(String id, String title, String source, List<String> tags, String audioUrl, Instant createdAt) {
        this(id, title, source, tags, audioUrl, createdAt, null, BigDecimal.ZERO);
    }

    public MyLibraryItemResponse(String id, String title, String source, List<String> tags, String audioUrl, Instant createdAt, String msisdn) {
        this(id, title, source, tags, audioUrl, createdAt, msisdn, BigDecimal.ZERO);
    }
}
