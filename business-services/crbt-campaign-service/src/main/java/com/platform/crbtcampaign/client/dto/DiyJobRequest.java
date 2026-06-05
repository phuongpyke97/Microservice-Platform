package com.platform.crbtcampaign.client.dto;

public record DiyJobRequest(
    String prompt,
    String voiceId,
    String type,
    String audioFileKey,
    Double vocalStart,
    Double vocalEnd,
    String title,
    String msisdn
) {}
