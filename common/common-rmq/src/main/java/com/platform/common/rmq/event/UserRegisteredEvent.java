package com.platform.common.rmq.event;

public record UserRegisteredEvent(Long userId, String email, String msisdn, long timestamp) {
}
