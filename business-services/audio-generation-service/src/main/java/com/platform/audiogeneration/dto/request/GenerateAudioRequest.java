package com.platform.audiogeneration.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerateAudioRequest(
    @NotBlank @Size(max = 500) String prompt,
    String voiceId
) {}
