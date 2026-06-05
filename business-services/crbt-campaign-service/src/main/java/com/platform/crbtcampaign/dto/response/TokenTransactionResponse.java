package com.platform.crbtcampaign.dto.response;

import java.time.Instant;

public record TokenTransactionResponse(
    Long id,
    String transactionType,
    int amount,
    int balanceBefore,
    int balanceAfter,
    Instant createdAt
) {}
