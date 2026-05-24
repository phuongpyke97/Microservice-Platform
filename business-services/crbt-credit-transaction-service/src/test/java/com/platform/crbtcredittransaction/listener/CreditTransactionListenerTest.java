package com.platform.crbtcredittransaction.listener;

import static org.mockito.Mockito.verify;

import com.platform.common.rmq.event.CreditChangedEvent;
import com.platform.crbtcredittransaction.service.CreditTransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreditTransactionListenerTest {

    @Mock
    private CreditTransactionService creditTransactionService;

    @InjectMocks
    private CreditTransactionListener listener;

    @Test
    void onCreditChanged_shouldCallServiceSave() {
        CreditChangedEvent event = new CreditChangedEvent(1L, 100, "IN", "Recharge", "REF-1", System.currentTimeMillis());

        listener.onCreditChanged(event);

        verify(creditTransactionService).save(event);
    }
}
