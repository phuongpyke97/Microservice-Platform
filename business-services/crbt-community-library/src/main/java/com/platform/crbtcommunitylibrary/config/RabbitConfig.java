package com.platform.crbtcommunitylibrary.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.rmq.RmqExchanges;
import com.platform.common.rmq.RmqQueues;
import com.platform.common.rmq.RmqRoutingKeys;
import com.platform.common.rmq.config.RabbitDlqConfig;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange audioEventsExchange() {
        return new TopicExchange(RmqExchanges.AUDIO_EVENTS);
    }

    @Bean
    public Queue libraryAudioGeneratedQueue() {
        return RabbitDlqConfig.dlqEnabledQueue(RmqQueues.LIBRARY_AUDIO_GENERATED);
    }

    @Bean
    public Binding libraryAudioGeneratedBinding() {
        return BindingBuilder.bind(libraryAudioGeneratedQueue())
                .to(audioEventsExchange())
                .with(RmqRoutingKeys.AUDIO_GENERATED);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
