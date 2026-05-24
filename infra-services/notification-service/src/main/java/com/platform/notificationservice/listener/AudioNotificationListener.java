package com.platform.notificationservice.listener;

import com.platform.common.rmq.RmqQueues;
import com.platform.common.rmq.event.AudioGeneratedEvent;
import com.platform.notificationservice.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AudioNotificationListener {

    private final NotificationService notificationService;

    public AudioNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RmqQueues.AUDIO_GENERATED)
    public void onAudioGenerated(AudioGeneratedEvent event) {
        if ("COMPLETED".equalsIgnoreCase(event.status())) {
            notificationService.sendAudioReady(event.userId(), event.jobId(), event.audioUrl());
        }
    }
}
