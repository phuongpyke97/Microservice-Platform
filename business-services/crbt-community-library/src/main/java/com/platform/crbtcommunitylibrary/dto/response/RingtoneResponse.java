package com.platform.crbtcommunitylibrary.dto.response;

import java.time.Instant;

public record RingtoneResponse(
    Long id,
    String title,
    String artistName,
    String audioUrl,
    String coverImageUrl,
    int durationSeconds,
    boolean featured,
    CategoryResponse category,
    Instant createdAt,
    Instant updatedAt
) {
}
