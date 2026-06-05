package com.platform.audiogeneration.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerateAudioRequest(
    @NotBlank @Size(max = 500) String prompt,
    String voiceId,
    String type, // "AI" or "DIY"
    String audioFileKey,
    Double vocalStart,
    Double vocalEnd,
    String title,
    String msisdn
) {
    public GenerateAudioRequest(String prompt, String voiceId, String type, String audioFileKey, Double vocalStart, Double vocalEnd) {
        this(prompt, voiceId, type, audioFileKey, vocalStart, vocalEnd, null, null);
    }
}
