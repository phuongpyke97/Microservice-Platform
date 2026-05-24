package com.platform.crbtcampaign.dto.response;

import java.math.BigDecimal;

public record PackageResponse(
    Long id,
    String name,
    BigDecimal price,
    int creditAmount,
    int validityDays
) {}
