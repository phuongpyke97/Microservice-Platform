package com.platform.creditwallet.listener;

import com.platform.common.rmq.RmqQueues;
import com.platform.common.rmq.event.PaymentResultEvent;
import com.platform.creditwallet.service.WalletService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentResultListener {

    private final WalletService walletService;

    public PaymentResultListener(WalletService walletService) {
        this.walletService = walletService;
    }

    @RabbitListener(queues = RmqQueues.PAYMENT_RESULT)
    public void onPaymentResult(PaymentResultEvent event) {
        if ("SUCCESS".equalsIgnoreCase(event.status()) && event.creditAmount() > 0) {
            walletService.addCredit(event.userId(), event.creditAmount(),
                    "payment_success", event.transactionId());
        }
    }
}
