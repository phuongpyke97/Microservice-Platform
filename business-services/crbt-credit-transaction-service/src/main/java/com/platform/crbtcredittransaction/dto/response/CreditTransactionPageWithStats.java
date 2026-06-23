package com.platform.crbtcredittransaction.dto.response;

import com.platform.common.core.response.PageResponse;

public record CreditTransactionPageWithStats(
    PageResponse<CreditTransactionResponse> page,
    CreditTransactionStats stats
) {}
