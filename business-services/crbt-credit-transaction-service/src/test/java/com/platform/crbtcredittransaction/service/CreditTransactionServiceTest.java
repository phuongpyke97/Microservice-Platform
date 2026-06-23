package com.platform.crbtcredittransaction.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.common.core.exception.BaseException;
import com.platform.common.rmq.event.CreditChangedEvent;
import com.platform.crbtcredittransaction.entity.CreditTransaction;
import com.platform.crbtcredittransaction.repository.CreditTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreditTransactionServiceTest {

    @Mock
    private CreditTransactionRepository repository;

    @InjectMocks
    private CreditTransactionService service;

    @Test
    void save_shouldSaveTransaction() {
        CreditChangedEvent event = new CreditChangedEvent(1L, 100, "IN", "Recharge", "REF-1", System.currentTimeMillis());

        service.save(event);

        verify(repository).save(any(CreditTransaction.class));
    }

    @Test
    void query_shouldThrowWhenInvalidDateRange() {
        assertThrows(BaseException.class, () ->
            service.query(1L, null, null, 1000L, 500L, null)
        );
    }

    @Test
    void exportCsv_shouldReturnCsvString() {
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
            .thenReturn(java.util.List.of(
                new CreditTransaction(1L, 100, "IN", "Reason", "REF-1", 1000L)
            ));

        String csv = service.exportCsv(1L, null, null, null, null);

        assertNotNull(csv);
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("ID,User ID,Before Balance,After Balance,Amount,Direction,Gen Type,Model,Is Free,Reason,Reference ID,Timestamp,Created At"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("1,,,100,IN,OTHER,,false,\"Reason\",REF-1,1000"));
    }

    @Test
    void getStatsByUserIds_shouldReturnCorrectStats() {
        java.util.List<Object[]> mockRows = java.util.List.of(
            new Object[]{1L, "ADD", 10L},
            new Object[]{1L, "DEDUCT", 3L},
            new Object[]{2L, "ADD", 5L}
        );
        when(repository.getStatsByUserIds(java.util.List.of(1L, 2L, 3L))).thenReturn(mockRows);

        java.util.Map<Long, com.platform.crbtcredittransaction.dto.response.UserCreditStats> stats = service.getStatsByUserIds(java.util.List.of(1L, 2L, 3L));

        assertNotNull(stats);
        assertEquals(3, stats.size());
        assertEquals(10L, stats.get(1L).getPurchased());
        assertEquals(3L, stats.get(1L).getUsed());
        assertEquals(5L, stats.get(2L).getPurchased());
        assertEquals(0L, stats.get(2L).getUsed());
        assertEquals(0L, stats.get(3L).getPurchased());
        assertEquals(0L, stats.get(3L).getUsed());
    }
}
