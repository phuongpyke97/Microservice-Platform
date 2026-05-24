package com.platform.payment.dto.response;

import com.platform.payment.entity.PaymentStatus;

public record PaymentResponse(
        Long transactionId,
        String idempotencyKey,
        PaymentStatus status,
        String providerReference,
        String packageCode,
        long amountMmk,
        int creditAmount
) {
}
