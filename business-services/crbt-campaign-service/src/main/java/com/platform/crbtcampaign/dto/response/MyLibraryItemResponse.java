package com.platform.crbtcampaign.dto.response;

import java.time.Instant;
import java.util.List;

public record MyLibraryItemResponse(
    String id,
    String title,
    String source, // "AI" or "DIY"
    List<String> tags, // e.g. ["pop", "happy", "piano"] for AI, or ["DIY"] for DIY
    String audioUrl,
    Instant createdAt,
    String msisdn
) {
    public MyLibraryItemResponse(String id, String title, String source, List<String> tags, String audioUrl, Instant createdAt) {
        this(id, title, source, tags, audioUrl, createdAt, null);
    }
}
