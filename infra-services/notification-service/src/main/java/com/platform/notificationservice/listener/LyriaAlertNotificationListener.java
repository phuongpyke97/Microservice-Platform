package com.platform.notificationservice.listener;

import com.platform.common.rmq.RmqQueues;
import com.platform.common.rmq.event.LyriaCostAlertEvent;
import com.platform.notificationservice.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class LyriaAlertNotificationListener {

    private final NotificationService notificationService;

    public LyriaAlertNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RmqQueues.LYRIA_COST_ALERT)
    public void onLyriaCostAlert(LyriaCostAlertEvent event) {
        notificationService.sendLyriaCostAlert(
            event.email(),
            event.thresholdCost(),
            event.currentCost(),
            event.statDate().toString()
        );
    }
}
