package com.platform.crbtcampaign.dto.response;

import java.time.Instant;

/** Lightweight row for the version-history table (no heavy template/pool payload). */
public record LyriaPromptVersionResponse(
    Long id,
    String model,
    int version,
    String status,
    String createdBy,
    Instant createdAt,
    Instant activatedAt,
    Instant deactivatedAt
) {}
