package com.platform.payment.client;

public record MpsChargeRequest(String msisdn, long amountMmk, String packageCode, String idempotencyKey) {
}
