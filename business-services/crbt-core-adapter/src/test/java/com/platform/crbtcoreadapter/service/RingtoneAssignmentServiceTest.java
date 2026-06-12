package com.platform.crbtcoreadapter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.crbtcoreadapter.client.MytoneCmsClient;
import com.platform.crbtcoreadapter.client.MytoneCmsRequest;
import com.platform.crbtcoreadapter.client.MytoneCmsResponse;
import com.platform.crbtcoreadapter.entity.RingtoneAssignment;
import com.platform.crbtcoreadapter.entity.RingtoneAssignment.SyncStatus;
import com.platform.crbtcoreadapter.repository.RingtoneAssignmentRepository;
import com.platform.common.rmq.config.RabbitDlqConfig;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class RingtoneAssignmentServiceTest {

    @Mock RingtoneAssignmentRepository repository;
    @Mock MytoneCmsClient mytoneClient;
    @Mock RabbitTemplate rabbitTemplate;

    @InjectMocks RingtoneAssignmentService service;

    @Test
    void syncToMytoneAsync_shouldSucceed() {
        RingtoneAssignment assignment = new RingtoneAssignment(1L, "959123456", "http://audio.wav");
        assignment.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(assignment));
        when(mytoneClient.assignRingtone(any(MytoneCmsRequest.class)))
            .thenReturn(new MytoneCmsResponse(true, "TX-123", "Success"));

        service.syncToMytoneAsync(1L);

        assertEquals(SyncStatus.ACTIVE, assignment.getStatus());
        assertEquals("TX-123", assignment.getMytoneTransactionId());
    }

    @Test
    void syncToMytoneAsync_shouldFailAndRetry() {
        RingtoneAssignment assignment = new RingtoneAssignment(1L, "959123456", "http://audio.wav");
        assignment.setId(1L);
        assignment.setRetryCount(0);
        when(repository.findById(1L)).thenReturn(Optional.of(assignment));
        when(mytoneClient.assignRingtone(any(MytoneCmsRequest.class)))
            .thenReturn(new MytoneCmsResponse(false, null, "Internal Error"));

        service.syncToMytoneAsync(1L);

        assertEquals(SyncStatus.FAILED, assignment.getStatus());
        assertEquals(1, assignment.getRetryCount());
    }

    @Test
    void syncToMytoneAsync_shouldSendToDlq_whenMaxRetriesExhausted() {
        RingtoneAssignment assignment = new RingtoneAssignment(1L, "959123456", "http://audio.wav");
        assignment.setId(1L);
        assignment.setRetryCount(2); // Current count is 2, next fail will make it 3 (MAX)
        when(repository.findById(1L)).thenReturn(Optional.of(assignment));
        when(mytoneClient.assignRingtone(any(MytoneCmsRequest.class)))
            .thenReturn(new MytoneCmsResponse(false, null, "Final Error"));

        service.syncToMytoneAsync(1L);

        assertEquals(3, assignment.getRetryCount());
        verify(rabbitTemplate).convertAndSend(eq(RabbitDlqConfig.DEAD_LETTER_EXCHANGE), eq(RabbitDlqConfig.DEAD_LETTER_ROUTING_KEY), any(String.class));
    }

    @Test
    void getActiveRingtoneUrls_shouldReturnOnlyActiveUrls() {
        java.util.List<String> urls = java.util.List.of("http://url1", "http://url2");
        RingtoneAssignment activeAss = new RingtoneAssignment(1L, "959123456", "http://url1");
        activeAss.setStatus(SyncStatus.ACTIVE);
        
        when(repository.findByRingtoneUrlInAndStatusIn(urls, java.util.List.of(SyncStatus.ACTIVE, SyncStatus.SYNCING)))
            .thenReturn(java.util.List.of(activeAss));

        java.util.List<String> result = service.getActiveRingtoneUrls(urls);
        assertEquals(1, result.size());
        assertEquals("http://url1", result.get(0));
    }
}
