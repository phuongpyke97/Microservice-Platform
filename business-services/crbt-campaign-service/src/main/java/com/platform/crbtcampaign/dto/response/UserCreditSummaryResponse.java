package com.platform.crbtcampaign.dto.response;

public record UserCreditSummaryResponse(
        Long userId,
        String msisdn,
        Long purchased,
        Long used,
        Integer remaining,
        String status,
        String packageName,
        String purchaseDate,
        String expiryDate
) {}
