package com.platform.payment.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ChargeRequest(
        @NotBlank String idempotencyKey,
        @NotBlank String packageCode,
        @Min(1) long amountMmk,
        @Min(1) int creditAmount
) {
}
