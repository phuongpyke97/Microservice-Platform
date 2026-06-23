package com.platform.crbtcampaign.dto.response;

import java.time.Instant;
import java.util.List;

public record LyriaPromptResponse(
    Long id,
    String model,
    int version,
    String promptTemplate,
    List<String> keys,
    List<String> secondaryInstrumentations,
    List<String> tempoGrooves,
    List<String> acousticEnvironments,
    String status,
    String createdBy,
    Instant createdAt,
    Instant activatedAt,
    Instant deactivatedAt
) {}
