package com.platform.crbtcommunitylibrary.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RingtoneRequest(
    @NotBlank @Size(max = 150) String title,
    @NotBlank @Size(max = 100) String artistName,
    @NotBlank @Size(max = 500) String audioUrl,
    @Size(max = 500) String coverImageUrl,
    Integer durationSeconds,
    boolean featured,
    @NotNull Long moodId,
    Boolean status,
    @NotNull Long categoryId
) {
}
