package com.platform.notificationservice.config;

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
    public TopicExchange userEventsExchange() {
        return new TopicExchange(RmqExchanges.USER_EVENTS);
    }

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(RmqExchanges.PAYMENT_EVENTS);
    }

    @Bean
    public TopicExchange audioEventsExchange() {
        return new TopicExchange(RmqExchanges.AUDIO_EVENTS);
    }

    @Bean
    public TopicExchange auditEventsExchange() {
        return new TopicExchange(RmqExchanges.AUDIT_EVENTS);
    }

    @Bean
    public Queue userRegisteredQueue() {
        return RabbitDlqConfig.dlqEnabledQueue(RmqQueues.USER_REGISTERED);
    }

    @Bean
    public Queue userPasswordResetQueue() {
        return RabbitDlqConfig.dlqEnabledQueue(RmqQueues.USER_PASSWORD_RESET);
    }

    @Bean
    public Queue notificationPaymentQueue() {
        return RabbitDlqConfig.dlqEnabledQueue(RmqQueues.NOTIFICATION_PAYMENT);
    }

    @Bean
    public Queue audioGeneratedQueue() {
        return RabbitDlqConfig.dlqEnabledQueue(RmqQueues.AUDIO_GENERATED);
    }

    @Bean
    public Queue lyriaCostAlertQueue() {
        return RabbitDlqConfig.dlqEnabledQueue(RmqQueues.LYRIA_COST_ALERT);
    }

    @Bean
    public Binding userRegisteredBinding() {
        return BindingBuilder.bind(userRegisteredQueue()).to(userEventsExchange()).with(RmqRoutingKeys.USER_REGISTERED);
    }

    @Bean
    public Binding userPasswordResetBinding() {
        return BindingBuilder.bind(userPasswordResetQueue()).to(userEventsExchange()).with(RmqRoutingKeys.USER_PASSWORD_RESET);
    }

    @Bean
    public Binding paymentResultBinding() {
        return BindingBuilder.bind(notificationPaymentQueue()).to(paymentEventsExchange()).with(RmqRoutingKeys.PAYMENT_RESULT);
    }

    @Bean
    public Binding audioGeneratedBinding() {
        return BindingBuilder.bind(audioGeneratedQueue()).to(audioEventsExchange()).with(RmqRoutingKeys.AUDIO_GENERATED);
    }

    @Bean
    public Binding lyriaCostAlertBinding() {
        return BindingBuilder.bind(lyriaCostAlertQueue()).to(auditEventsExchange()).with(RmqRoutingKeys.LYRIA_COST_ALERT);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
