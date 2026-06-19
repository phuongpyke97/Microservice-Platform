package com.platform.crbtcampaign.dto.response;

public record UserLyriaHistoryResponse(
    Long id,
    Long userId,
    String msisdn,
    String title,
    String genre,
    String mood,
    String instrument,
    String audioUrl,
    int durationSeconds
) {}
