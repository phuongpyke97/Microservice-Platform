package com.platform.crbtcommunitylibrary.dto.response;

import java.time.Instant;

public record MoodResponse(
    Long id,
    String name,
    String description,
    Instant createdAt,
    Instant updatedAt
) {
}
