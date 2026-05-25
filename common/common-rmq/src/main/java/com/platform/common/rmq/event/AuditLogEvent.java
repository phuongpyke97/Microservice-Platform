package com.platform.common.rmq.event;

import java.io.Serializable;

public record AuditLogEvent(Long userId, String action, String sourceIp, String status,
                             String metadataJson, long timestamp) implements Serializable {
    private static final long serialVersionUID = 1L;
}
