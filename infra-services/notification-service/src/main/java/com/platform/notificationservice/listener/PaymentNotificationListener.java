package com.platform.notificationservice.listener;

import com.platform.common.rmq.RmqQueues;
import com.platform.common.rmq.event.PaymentResultEvent;
import com.platform.notificationservice.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentNotificationListener {

    private final NotificationService notificationService;

    public PaymentNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RmqQueues.NOTIFICATION_PAYMENT)
    public void onPaymentResult(PaymentResultEvent event) {
        if ("SUCCESS".equalsIgnoreCase(event.status())) {
            notificationService.sendPaymentConfirmation(event.userId(), event.packageCode(), event.status());
        }
    }
}
