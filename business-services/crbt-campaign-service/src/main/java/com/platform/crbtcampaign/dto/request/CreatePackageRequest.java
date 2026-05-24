package com.platform.crbtcampaign.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreatePackageRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull @DecimalMin("0.0") BigDecimal price,
    @Min(1) int creditAmount,
    @Min(1) int validityDays
) {}
