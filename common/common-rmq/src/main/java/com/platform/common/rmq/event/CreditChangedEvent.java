package com.platform.common.rmq.event;

public record CreditChangedEvent(Long userId, int amount, String direction, String reason,
                                  String referenceId, long timestamp, Boolean isFree, String genType,
                                  Integer beforeBalance, Integer afterBalance, String model) {

    public CreditChangedEvent(Long userId, int amount, String direction, String reason,
                              String referenceId, long timestamp, Boolean isFree, String genType) {
        this(userId, amount, direction, reason, referenceId, timestamp, isFree, genType, null, null, null);
    }

    public CreditChangedEvent(Long userId, int amount, String direction, String reason,
                              String referenceId, long timestamp) {
        this(userId, amount, direction, reason, referenceId, timestamp, false, "OTHER", null, null, null);
    }
}
