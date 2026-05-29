package com.platform.creditwallet.config;

import com.platform.common.rmq.RmqExchanges;
import com.platform.common.rmq.RmqQueues;
import com.platform.common.rmq.RmqRoutingKeys;
import com.platform.common.rmq.config.RabbitDlqConfig;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange creditEventsExchange() {
        return new TopicExchange(RmqExchanges.CREDIT_EVENTS, true, false);
    }

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(RmqExchanges.PAYMENT_EVENTS, true, false);
    }

    @Bean
    public Queue paymentResultQueue() {
        return RabbitDlqConfig.dlqEnabledQueue(RmqQueues.PAYMENT_RESULT);
    }

    @Bean
    public Binding paymentResultBinding(Queue paymentResultQueue, TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(paymentResultQueue).to(paymentEventsExchange).with(RmqRoutingKeys.PAYMENT_RESULT);
    }

    @Bean
    public Queue creditChangedQueue() {
        return RabbitDlqConfig.dlqEnabledQueue(RmqQueues.CREDIT_CHANGED);
    }

    @Bean
    public Binding creditChangedBinding(Queue creditChangedQueue, TopicExchange creditEventsExchange) {
        return BindingBuilder.bind(creditChangedQueue).to(creditEventsExchange).with(RmqRoutingKeys.CREDIT_CHANGED);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(org.springframework.amqp.rabbit.connection.ConnectionFactory cf,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
