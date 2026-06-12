package com.platform.crbtcampaign.scheduler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.common.core.response.ApiResponse;
import com.platform.crbtcampaign.client.CrbtCoreAdapterClient;
import com.platform.crbtcampaign.client.FileServiceClient;
import com.platform.crbtcampaign.entity.UserLyriaHistory;
import com.platform.crbtcampaign.repository.UserLyriaHistoryRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserLyriaHistoryCleanupSchedulerTest {

    @Mock
    private UserLyriaHistoryRepository historyRepository;

    @Mock
    private CrbtCoreAdapterClient crbtCoreAdapterClient;

    @Mock
    private FileServiceClient fileServiceClient;

    @InjectMocks
    private UserLyriaHistoryCleanupScheduler cleanupScheduler;

    @Test
    void cleanupStaleLyriaHistory_noCandidates_doesNothing() {
        when(historyRepository.findByIdGreaterThanAndDeletedFalseAndCreatedAtBeforeOrderByIdAsc(
                eq(0L), any(Instant.class), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        cleanupScheduler.cleanupStaleLyriaHistory();

        verify(crbtCoreAdapterClient, never()).activeCheck(any());
        verify(fileServiceClient, never()).deleteFileByUrl(any());
    }

    @Test
    void cleanupStaleLyriaHistory_activeCheckFails_abortsLoop() {
        UserLyriaHistory item = mock(UserLyriaHistory.class);
        when(item.getId()).thenReturn(1L);
        when(item.getAudioUrl()).thenReturn("http://example.com/audio1.mp3");

        when(historyRepository.findByIdGreaterThanAndDeletedFalseAndCreatedAtBeforeOrderByIdAsc(
                eq(0L), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(item));

        when(crbtCoreAdapterClient.activeCheck(List.of("http://example.com/audio1.mp3")))
                .thenReturn(ApiResponse.error("ERROR", "Active check service failed"));

        cleanupScheduler.cleanupStaleLyriaHistory();

        verify(fileServiceClient, never()).deleteFileByUrl(any());
        verify(item, never()).setDeleted(any(Boolean.class));
        verify(historyRepository, never()).save(any());
    }

    @Test
    void cleanupStaleLyriaHistory_activeCheckThrows_abortsLoop() {
        UserLyriaHistory item = mock(UserLyriaHistory.class);
        when(item.getId()).thenReturn(1L);
        when(item.getAudioUrl()).thenReturn("http://example.com/audio1.mp3");

        when(historyRepository.findByIdGreaterThanAndDeletedFalseAndCreatedAtBeforeOrderByIdAsc(
                eq(0L), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(item));

        when(crbtCoreAdapterClient.activeCheck(any())).thenThrow(new RuntimeException("Core adapter connection failed"));

        cleanupScheduler.cleanupStaleLyriaHistory();

        verify(fileServiceClient, never()).deleteFileByUrl(any());
        verify(item, never()).setDeleted(any(Boolean.class));
        verify(historyRepository, never()).save(any());
    }

    @Test
    void cleanupStaleLyriaHistory_processesDifferentCasesCorrectly() {
        // Mock candidates
        UserLyriaHistory item1 = mock(UserLyriaHistory.class);
        when(item1.getId()).thenReturn(1L);
        when(item1.getAudioUrl()).thenReturn("http://example.com/active.mp3"); // active ringback tone

        UserLyriaHistory item2 = mock(UserLyriaHistory.class);
        when(item2.getId()).thenReturn(2L);
        when(item2.getAudioUrl()).thenReturn("http://example.com/inactive.mp3"); // inactive, delete successfully

        UserLyriaHistory item3 = mock(UserLyriaHistory.class);
        when(item3.getId()).thenReturn(3L);
        when(item3.getAudioUrl()).thenReturn("http://example.com/missing.mp3"); // inactive, delete returns 404 FILE_NOT_FOUND

        UserLyriaHistory item4 = mock(UserLyriaHistory.class);
        when(item4.getId()).thenReturn(4L);
        when(item4.getAudioUrl()).thenReturn("http://example.com/fail.mp3"); // inactive, delete returns 500 internal error

        UserLyriaHistory item5 = mock(UserLyriaHistory.class);
        when(item5.getId()).thenReturn(5L);
        when(item5.getAudioUrl()).thenReturn(null); // null audio URL, should soft delete immediately

        UserLyriaHistory item6 = mock(UserLyriaHistory.class);
        when(item6.getId()).thenReturn(6L);
        when(item6.getAudioUrl()).thenReturn("   "); // blank audio URL, should soft delete immediately

        List<UserLyriaHistory> batch = List.of(item1, item2, item3, item4, item5, item6);

        when(historyRepository.findByIdGreaterThanAndDeletedFalseAndCreatedAtBeforeOrderByIdAsc(
                eq(0L), any(Instant.class), any(Pageable.class)))
                .thenReturn(batch);

        // Next page is empty
        when(historyRepository.findByIdGreaterThanAndDeletedFalseAndCreatedAtBeforeOrderByIdAsc(
                eq(6L), any(Instant.class), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        // Check active check setup
        List<String> expectedUrlsToCheck = List.of(
                "http://example.com/active.mp3",
                "http://example.com/inactive.mp3",
                "http://example.com/missing.mp3",
                "http://example.com/fail.mp3"
        );
        when(crbtCoreAdapterClient.activeCheck(expectedUrlsToCheck))
                .thenReturn(ApiResponse.success(List.of("http://example.com/active.mp3")));

        // File service setup
        when(fileServiceClient.deleteFileByUrl("http://example.com/inactive.mp3"))
                .thenReturn(ApiResponse.success(null));
        when(fileServiceClient.deleteFileByUrl("http://example.com/missing.mp3"))
                .thenReturn(ApiResponse.error("FILE_NOT_FOUND", "File not found"));
        when(fileServiceClient.deleteFileByUrl("http://example.com/fail.mp3"))
                .thenReturn(ApiResponse.error("INTERNAL_ERROR", "Server error"));

        cleanupScheduler.cleanupStaleLyriaHistory();

        // Verifications
        // Item 1: active, should NOT be deleted, setDeleted(true) should NOT be called
        verify(fileServiceClient, never()).deleteFileByUrl("http://example.com/active.mp3");
        verify(item1, never()).setDeleted(any(Boolean.class));
        verify(historyRepository, never()).save(item1);

        // Item 2: inactive, deleted successfully, should setDeleted(true) and save
        verify(fileServiceClient, times(1)).deleteFileByUrl("http://example.com/inactive.mp3");
        verify(item2, times(1)).setDeleted(true);
        verify(historyRepository, times(1)).save(item2);

        // Item 3: inactive, missing (404), should setDeleted(true) and save
        verify(fileServiceClient, times(1)).deleteFileByUrl("http://example.com/missing.mp3");
        verify(item3, times(1)).setDeleted(true);
        verify(historyRepository, times(1)).save(item3);

        // Item 4: inactive, fail (500), should NOT setDeleted(true)
        verify(fileServiceClient, times(1)).deleteFileByUrl("http://example.com/fail.mp3");
        verify(item4, never()).setDeleted(any(Boolean.class));
        verify(historyRepository, never()).save(item4);

        // Item 5: null url, should setDeleted(true) and save without calling client
        verify(item5, times(1)).setDeleted(true);
        verify(historyRepository, times(1)).save(item5);

        // Item 6: blank url, should setDeleted(true) and save without calling client
        verify(item6, times(1)).setDeleted(true);
        verify(historyRepository, times(1)).save(item6);
    }
}
