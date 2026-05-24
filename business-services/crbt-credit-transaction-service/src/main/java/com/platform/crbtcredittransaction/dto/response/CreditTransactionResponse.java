package com.platform.crbtcredittransaction.dto.response;

import java.time.Instant;

public record CreditTransactionResponse(
    Long id,
    Long userId,
    int amount,
    String direction,
    String reason,
    String referenceId,
    long timestamp,
    Instant createdAt
) {
}
