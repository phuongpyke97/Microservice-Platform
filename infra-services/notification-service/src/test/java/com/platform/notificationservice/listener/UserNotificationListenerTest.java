package com.platform.notificationservice.listener;

import com.platform.common.rmq.event.UserPasswordResetEvent;
import com.platform.common.rmq.event.UserRegisteredEvent;
import com.platform.notificationservice.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserNotificationListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private UserNotificationListener listener;

    @Test
    void onUserRegistered_callsSendWelcome() {
        var event = new UserRegisteredEvent(1L, "user@example.com", "959123456", System.currentTimeMillis());

        listener.onUserRegistered(event);

        verify(notificationService).sendWelcome("user@example.com", "959123456");
    }

    @Test
    void onPasswordReset_callsSendOtp() {
        var event = new UserPasswordResetEvent(1L, "user@example.com", "ABC123", System.currentTimeMillis());

        listener.onPasswordReset(event);

        verify(notificationService).sendOtp("user@example.com", "ABC123");
    }
}
