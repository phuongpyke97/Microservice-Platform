package com.platform.crbtcredittransaction.dto.response;

public record CreditTransactionStats(
    long totalCount,
    long totalAdd,
    long totalDeduct,
    long netFlow
) {}
