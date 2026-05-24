package com.platform.auditlogservice.listener;

import com.platform.auditlogservice.service.AuditLogService;
import com.platform.common.rmq.RmqQueues;
import com.platform.common.rmq.event.AuditLogEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AuditLogListener {

    private final AuditLogService auditLogService;

    public AuditLogListener(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @RabbitListener(queues = RmqQueues.AUDIT_LOG)
    public void onAuditLog(AuditLogEvent event) {
        auditLogService.save(event);
    }
}
