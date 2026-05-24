package com.platform.auditlogservice.listener;

import com.platform.auditlogservice.service.AuditLogService;
import com.platform.common.rmq.event.AuditLogEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogListenerTest {

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AuditLogListener listener;

    @Test
    void onAuditLog_callsServiceSave() {
        var event = new AuditLogEvent(1L, "login.failed", "127.0.0.1", "FAILED", null, System.currentTimeMillis());

        listener.onAuditLog(event);

        verify(auditLogService).save(event);
    }
}
