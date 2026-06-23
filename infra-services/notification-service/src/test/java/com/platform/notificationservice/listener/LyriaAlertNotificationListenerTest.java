package com.platform.notificationservice.listener;

import com.platform.common.rmq.event.LyriaCostAlertEvent;
import com.platform.notificationservice.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LyriaAlertNotificationListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private LyriaAlertNotificationListener listener;

    @Test
    void onLyriaCostAlert_callsSendLyriaCostAlert() {
        LocalDate date = LocalDate.of(2026, 6, 24);
        var event = new LyriaCostAlertEvent(
            "admin@platform.com",
            new BigDecimal("100.00"),
            new BigDecimal("105.50"),
            date
        );

        listener.onLyriaCostAlert(event);

        verify(notificationService).sendLyriaCostAlert(
            "admin@platform.com",
            new BigDecimal("100.00"),
            new BigDecimal("105.50"),
            "2026-06-24"
        );
    }
}
