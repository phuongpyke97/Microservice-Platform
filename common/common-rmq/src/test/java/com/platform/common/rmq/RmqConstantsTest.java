package com.platform.common.rmq;

import com.platform.common.rmq.config.RabbitDlqConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RmqConstantsTest {

    @Test
    void exchangesAreNonBlank() {
        assertThat(RmqExchanges.USER_EVENTS).isNotBlank();
        assertThat(RmqExchanges.PAYMENT_EVENTS).isNotBlank();
        assertThat(RmqExchanges.CREDIT_EVENTS).isNotBlank();
        assertThat(RmqExchanges.AUDIO_EVENTS).isNotBlank();
        assertThat(RmqExchanges.AUDIT_EVENTS).isNotBlank();
    }

    @Test
    void queuesAreNonBlank() {
        assertThat(RmqQueues.USER_REGISTERED).isNotBlank();
        assertThat(RmqQueues.USER_PASSWORD_RESET).isNotBlank();
        assertThat(RmqQueues.PAYMENT_RESULT).isNotBlank();
        assertThat(RmqQueues.CREDIT_CHANGED).isNotBlank();
        assertThat(RmqQueues.AUDIO_GENERATED).isNotBlank();
        assertThat(RmqQueues.AUDIT_LOG).isNotBlank();
    }

    @Test
    void routingKeysAreNonBlank() {
        assertThat(RmqRoutingKeys.USER_REGISTERED).isNotBlank();
        assertThat(RmqRoutingKeys.PAYMENT_RESULT).isNotBlank();
        assertThat(RmqRoutingKeys.CREDIT_CHANGED).isNotBlank();
        assertThat(RmqRoutingKeys.AUDIO_GENERATED).isNotBlank();
        assertThat(RmqRoutingKeys.AUDIT_LOG).isNotBlank();
    }

    @Test
    void dlqArgumentsSetDeadLetterExchangeAndKey() {
        var args = RabbitDlqConfig.dlqArguments();

        assertThat(args).containsEntry("x-dead-letter-exchange", RabbitDlqConfig.DEAD_LETTER_EXCHANGE);
        assertThat(args).containsEntry("x-dead-letter-routing-key", RabbitDlqConfig.DEAD_LETTER_ROUTING_KEY);
    }

    @Test
    void dlqEnabledQueueIsDurableWithDlqArgs() {
        var queue = RabbitDlqConfig.dlqEnabledQueue("test-queue");

        assertThat(queue.getName()).isEqualTo("test-queue");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", RabbitDlqConfig.DEAD_LETTER_EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", RabbitDlqConfig.DEAD_LETTER_ROUTING_KEY);
    }
}
