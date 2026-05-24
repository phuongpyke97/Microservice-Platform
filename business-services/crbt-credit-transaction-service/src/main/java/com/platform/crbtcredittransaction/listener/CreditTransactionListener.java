package com.platform.crbtcredittransaction.listener;

import com.platform.common.rmq.RmqQueues;
import com.platform.common.rmq.event.CreditChangedEvent;
import com.platform.crbtcredittransaction.service.CreditTransactionService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class CreditTransactionListener {

    private final CreditTransactionService creditTransactionService;

    public CreditTransactionListener(CreditTransactionService creditTransactionService) {
        this.creditTransactionService = creditTransactionService;
    }

    @RabbitListener(queues = RmqQueues.CREDIT_TRANSACTION_HISTORY)
    public void onCreditChanged(CreditChangedEvent event) {
        creditTransactionService.save(event);
    }
}
