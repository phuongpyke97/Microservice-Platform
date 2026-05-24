package com.platform.notificationservice.listener;

import com.platform.common.rmq.RmqQueues;
import com.platform.common.rmq.event.UserPasswordResetEvent;
import com.platform.common.rmq.event.UserRegisteredEvent;
import com.platform.notificationservice.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class UserNotificationListener {

    private final NotificationService notificationService;

    public UserNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RmqQueues.USER_REGISTERED)
    public void onUserRegistered(UserRegisteredEvent event) {
        notificationService.sendWelcome(event.email(), event.msisdn());
    }

    @RabbitListener(queues = RmqQueues.USER_PASSWORD_RESET)
    public void onPasswordReset(UserPasswordResetEvent event) {
        notificationService.sendOtp(event.email(), event.otp());
    }
}
