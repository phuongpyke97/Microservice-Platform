package com.platform.creditwallet.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AmountRequest(
        @Min(1) int amount,
        @NotBlank String reason,
        String referenceId,
        Boolean isFree,
        String genType,
        String model
) {
    public AmountRequest(int amount, String reason, String referenceId, Boolean isFree, String genType) {
        this(amount, reason, referenceId, isFree, genType, null);
    }

    public AmountRequest(int amount, String reason, String referenceId) {
        this(amount, reason, referenceId, false, "OTHER", null);
    }
}
