package com.platform.common.rmq.event;

public record CreditChangedEvent(Long userId, int amount, String direction, String reason,
                                  String referenceId, long timestamp) {
}
