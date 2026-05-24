package com.platform.common.rmq.event;

public record PaymentResultEvent(Long userId, String transactionId, String packageCode,
                                  String status, int creditAmount, long timestamp) {
}
