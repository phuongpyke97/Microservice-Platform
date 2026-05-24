package com.platform.creditwallet.listener;

import com.platform.common.rmq.event.PaymentResultEvent;
import com.platform.creditwallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentResultListenerTest {

    @Mock private WalletService walletService;
    @InjectMocks private PaymentResultListener listener;

    @Test
    void onPaymentResult_success_addsCredit() {
        var event = new PaymentResultEvent(42L, "txn-1", "PKG_30", "SUCCESS", 30, 0L);

        listener.onPaymentResult(event);

        verify(walletService).addCredit(eq(42L), eq(30), eq("payment_success"), eq("txn-1"));
    }

    @Test
    void onPaymentResult_failedStatus_doesNothing() {
        var event = new PaymentResultEvent(42L, "txn-1", "PKG_30", "FAILED", 30, 0L);

        listener.onPaymentResult(event);

        verify(walletService, never()).addCredit(anyLong(), anyInt(), anyString(), anyString());
    }

    @Test
    void onPaymentResult_zeroCredit_doesNothing() {
        var event = new PaymentResultEvent(42L, "txn-1", "PKG_30", "SUCCESS", 0, 0L);

        listener.onPaymentResult(event);

        verify(walletService, never()).addCredit(anyLong(), anyInt(), anyString(), anyString());
    }
}
