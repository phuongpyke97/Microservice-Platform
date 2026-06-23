package com.platform.crbtcampaign.dto.response;

import com.platform.common.core.response.PageResponse;

public record UserCreditSummaryPageWithStats(
    PageResponse<UserCreditSummaryResponse> page,
    UserCreditSummaryStats stats
) {}
