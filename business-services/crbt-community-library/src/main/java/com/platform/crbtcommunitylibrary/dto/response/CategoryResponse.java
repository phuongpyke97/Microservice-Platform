package com.platform.crbtcommunitylibrary.dto.response;

import java.time.Instant;

public record CategoryResponse(
    Long id,
    String name,
    String description,
    Instant createdAt,
    Instant updatedAt
) {
}
