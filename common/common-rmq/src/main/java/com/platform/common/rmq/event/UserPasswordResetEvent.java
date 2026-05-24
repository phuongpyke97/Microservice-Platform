package com.platform.common.rmq.event;

public record UserPasswordResetEvent(Long userId, String email, String otp, long timestamp) {
}
