package com.platform.crbtcampaign.dto.response;

public record UserCreditSummaryStats(
    long totalUsers,
    long activeUsers,
    long totalPurchased,
    long totalUsed,
    long totalRemaining
) {}
