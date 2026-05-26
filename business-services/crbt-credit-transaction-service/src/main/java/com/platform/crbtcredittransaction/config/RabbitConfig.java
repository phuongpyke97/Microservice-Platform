package com.platform.crbtcredittransaction.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.rmq.RmqExchanges;
import com.platform.common.rmq.RmqQueues;
import com.platform.common.rmq.RmqRoutingKeys;
import com.platform.common.rmq.config.RabbitDlqConfig;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange creditEventsExchange() {
        return new TopicExchange(RmqExchanges.CREDIT_EVENTS);
    }

    @Bean
    public Queue creditTransactionHistoryQueue() {
        return RabbitDlqConfig.dlqEnabledQueue(RmqQueues.CREDIT_TRANSACTION_HISTORY);
    }

    @Bean
    public Binding creditTransactionHistoryBinding() {
        return BindingBuilder.bind(creditTransactionHistoryQueue()).to(creditEventsExchange()).with(RmqRoutingKeys.CREDIT_CHANGED);
    }

    @Bean
    public Binding creditTransactionHistoryDeductedBinding() {
        return BindingBuilder.bind(creditTransactionHistoryQueue()).to(creditEventsExchange()).with(RmqRoutingKeys.CREDIT_DEDUCTED);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }
}
