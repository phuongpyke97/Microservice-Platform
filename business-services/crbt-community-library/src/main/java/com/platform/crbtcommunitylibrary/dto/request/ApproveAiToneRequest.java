package com.platform.crbtcommunitylibrary.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ApproveAiToneRequest(
    @NotBlank String lyriaHistoryId,
    String title,
    String artistName,
    String coverImageUrl,
    Boolean featured,
    Boolean status,
    Long categoryId, // Optional override
    Long moodId      // Optional override
) {}
