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
    boolean status,
    long selectionCount,
    CategoryResponse category,
    MoodResponse mood,
    Instant createdAt,
    Instant updatedAt,
    Boolean isAiGenerated,
    String postedBy
) {}
