package com.platform.creditwallet.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AmountRequest(
        @Min(1) int amount,
        @NotBlank String reason,
        String referenceId
) {
}
