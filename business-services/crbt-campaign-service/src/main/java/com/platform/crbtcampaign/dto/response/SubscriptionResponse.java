package com.platform.crbtcampaign.dto.response;

import java.time.Instant;

public record SubscriptionResponse(
    Long subscriptionId,
    Long packageId,
    String packageName,
    double price,
    int validityDays,
    Instant expiresAt,
    int tokenBalance,
    Instant tokenExpiredAt,
    String status,
    boolean autoRenew
) {}
