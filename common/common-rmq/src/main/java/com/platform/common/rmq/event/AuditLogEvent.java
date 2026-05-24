package com.platform.common.rmq.event;

public record AuditLogEvent(Long userId, String action, String sourceIp, String status,
                             String metadataJson, long timestamp) {
}
