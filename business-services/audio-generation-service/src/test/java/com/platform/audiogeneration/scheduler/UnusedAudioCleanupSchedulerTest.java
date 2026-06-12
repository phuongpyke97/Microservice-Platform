package com.platform.audiogeneration.scheduler;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.platform.audiogeneration.client.CrbtCoreAdapterClient;
import com.platform.audiogeneration.client.FileServiceClient;
import com.platform.audiogeneration.entity.AudioJob;
import com.platform.audiogeneration.repository.AudioJobRepository;
import com.platform.common.core.response.ApiResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class UnusedAudioCleanupSchedulerTest {

    @Mock AudioJobRepository jobRepository;
    @Mock CrbtCoreAdapterClient crbtCoreAdapterClient;
    @Mock FileServiceClient fileServiceClient;

    @InjectMocks UnusedAudioCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        // any setups
    }

    @Test
    void cleanupUnusedAudios_shouldPurgeUnusedAudios() {
        AudioJob oldUnusedJob = new AudioJob(1L, "prompt", "voice");
        oldUnusedJob.setId(101L);
        oldUnusedJob.setResultUrl("http://localhost:9000/media-audio/unused.mp3");
        oldUnusedJob.setDeleted(false);

        AudioJob oldUsedJob = new AudioJob(2L, "prompt", "voice");
        oldUsedJob.setId(102L);
        oldUsedJob.setResultUrl("http://localhost:9000/media-audio/used.mp3");
        oldUsedJob.setDeleted(false);

        when(jobRepository.findByIdGreaterThanAndCreatedAtBeforeAndDeletedFalseOrderByIdAsc(
                eq(0L), any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of(oldUnusedJob, oldUsedJob));

        // Check active ringtone assignments: only used.mp3 is active
        when(crbtCoreAdapterClient.activeCheck(any()))
            .thenReturn(ApiResponse.success(List.of("http://localhost:9000/media-audio/used.mp3")));

        // Mock deletion endpoint: return success
        when(fileServiceClient.deleteFileByUrl("http://localhost:9000/media-audio/unused.mp3"))
            .thenReturn(ApiResponse.success(null));

        scheduler.cleanupUnusedAudios();

        // Verifications
        // Unused job should be deleted and marked deleted = true
        verify(fileServiceClient, times(1)).deleteFileByUrl("http://localhost:9000/media-audio/unused.mp3");
        assertTrue(oldUnusedJob.isDeleted());
        verify(jobRepository).save(oldUnusedJob);

        // Used job should NOT be deleted and should stay deleted = false
        verify(fileServiceClient, never()).deleteFileByUrl("http://localhost:9000/media-audio/used.mp3");
        assertFalse(oldUsedJob.isDeleted());
    }
}
