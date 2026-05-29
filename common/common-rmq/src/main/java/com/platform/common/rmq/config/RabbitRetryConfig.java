package com.platform.common.rmq.config;

import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Retry: 3 attempts with exponential backoff 1s → 2s → 4s. After the final failure
 * the message is republished to the dead-letter exchange via RepublishMessageRecoverer.
 */
@Configuration
public class RabbitRetryConfig {

    @Bean
    public Advice retryInterceptor(RabbitTemplate rabbitTemplate) {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000L, 2.0, 4000L)
                .recoverer(new RepublishMessageRecoverer(rabbitTemplate,
                        RabbitDlqConfig.DEAD_LETTER_EXCHANGE,
                        RabbitDlqConfig.DEAD_LETTER_ROUTING_KEY))
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                               Advice retryInterceptor,
                                                                               ObjectProvider<MessageConverter> messageConverterProvider) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAdviceChain(retryInterceptor);
        messageConverterProvider.ifAvailable(factory::setMessageConverter);
        return factory;
    }
}
