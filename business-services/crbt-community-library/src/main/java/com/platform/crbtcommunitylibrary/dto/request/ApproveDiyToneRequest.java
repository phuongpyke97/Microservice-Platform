package com.platform.crbtcommunitylibrary.dto.request;

import jakarta.validation.constraints.NotNull;

public record ApproveDiyToneRequest(
    @NotNull Long fileId,
    String title,
    String artistName,
    String coverImageUrl,
    Boolean featured,
    Boolean status,
    Long categoryId, // Optional override
    Long moodId      // Optional override
) {}
