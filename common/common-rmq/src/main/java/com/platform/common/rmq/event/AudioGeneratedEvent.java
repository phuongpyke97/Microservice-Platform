package com.platform.common.rmq.event;

public record AudioGeneratedEvent(Long userId, String jobId, String audioUrl, String status, long timestamp) {
}
