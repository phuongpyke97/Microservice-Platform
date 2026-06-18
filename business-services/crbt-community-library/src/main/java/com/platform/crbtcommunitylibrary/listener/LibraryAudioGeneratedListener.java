package com.platform.crbtcommunitylibrary.listener;

import com.platform.common.rmq.RmqQueues;
import com.platform.common.rmq.event.AudioGeneratedEvent;
import com.platform.crbtcommunitylibrary.service.RingtoneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class LibraryAudioGeneratedListener {

    private static final Logger log = LoggerFactory.getLogger(LibraryAudioGeneratedListener.class);
    private final RingtoneService ringtoneService;

    public LibraryAudioGeneratedListener(RingtoneService ringtoneService) {
        this.ringtoneService = ringtoneService;
    }

    @RabbitListener(queues = RmqQueues.LIBRARY_AUDIO_GENERATED)
    public void onAudioGenerated(AudioGeneratedEvent event) {
        log.info("Received AudioGeneratedEvent in community library: {}", event);
        if ("COMPLETED".equalsIgnoreCase(event.status())) {
            String audioFileKey = event.audioFileKey();
            if (audioFileKey != null && !audioFileKey.isBlank()) {
                try {
                    ringtoneService.incrementSelectionCountByKey(audioFileKey);
                } catch (Exception e) {
                    log.error("Failed to process selection count update for key: {}", audioFileKey, e);
                }
            }
        }
    }
}
