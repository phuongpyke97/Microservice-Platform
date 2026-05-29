package com.platform.creditwallet.listener;

import com.platform.common.rmq.RmqQueues;
import com.platform.common.rmq.event.CreditChangedEvent;
import com.platform.creditwallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class CreditChangedListener {

    private static final Logger log = LoggerFactory.getLogger(CreditChangedListener.class);
    private final WalletService walletService;

    public CreditChangedListener(WalletService walletService) {
        this.walletService = walletService;
    }

    @RabbitListener(queues = RmqQueues.CREDIT_CHANGED)
    public void onCreditChanged(CreditChangedEvent event) {
        log.info("[CREDIT-CHANGED-EVENT] Received event for userId={}, amount={}, direction={}, reason={}",
                event.userId(), event.amount(), event.direction(), event.reason());

        // Process only "IN" (incoming) events to prevent infinite loop of "ADD"/"DEDUCT" events published by wallet-service itself
        if ("IN".equalsIgnoreCase(event.direction())) {
            log.info("[CREDIT-CHANGED-EVENT] Processing campaign reward/incoming credit for userId={}, amount={}",
                    event.userId(), event.amount());
            walletService.addCredit(event.userId(), event.amount(), event.reason(), event.referenceId());
        } else {
            log.debug("[CREDIT-CHANGED-EVENT] Ignored event loop/outbound direction: {}", event.direction());
        }
    }
}
