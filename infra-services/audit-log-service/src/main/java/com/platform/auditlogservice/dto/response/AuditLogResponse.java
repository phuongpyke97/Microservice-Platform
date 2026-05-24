package com.platform.auditlogservice.dto.response;

import java.time.Instant;

public record AuditLogResponse(Long id, Long userId, String action, String sourceIp,
                               String status, String metadataJson, long timestamp, Instant createdAt) {
}
