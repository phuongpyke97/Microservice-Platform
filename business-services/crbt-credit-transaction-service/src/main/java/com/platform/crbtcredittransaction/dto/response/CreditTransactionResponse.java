package com.platform.crbtcredittransaction.dto.response;

import java.time.Instant;

public record CreditTransactionResponse(
    Long id,
    String msisdn,
    int amount,
    String direction,
    String reason,
    String referenceId,
    long timestamp,
    Instant createdAt,
    Boolean isFree,
    String genType,
    Integer beforeBalance,
    Integer afterBalance,
    String model
) {
    public CreditTransactionResponse(Long id, String msisdn, int amount, String direction, String reason, String referenceId, long timestamp, Instant createdAt, Boolean isFree, String genType) {
        this(id, msisdn, amount, direction, reason, referenceId, timestamp, createdAt, isFree, genType, null, null, null);
    }

    public CreditTransactionResponse(Long id, String msisdn, int amount, String direction, String reason, String referenceId, long timestamp, Instant createdAt) {
        this(id, msisdn, amount, direction, reason, referenceId, timestamp, createdAt, false, "OTHER", null, null, null);
    }
}
