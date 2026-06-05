package com.platform.crbtcampaign.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.ai.LyriaSystemPromptConfig;
import com.platform.common.core.response.ApiResponse;
import com.platform.crbtcampaign.client.AudioGenerationClient;
import com.platform.crbtcampaign.client.AuthServiceClient;
import com.platform.crbtcampaign.client.CreditWalletClient;
import com.platform.crbtcampaign.client.FileServiceClient;
import com.platform.crbtcampaign.client.LyriaClient;
import com.platform.crbtcampaign.client.dto.DiyJobResponse;
import com.platform.crbtcampaign.dto.response.MyLibraryItemResponse;
import com.platform.crbtcampaign.entity.UserLyriaHistory;
import com.platform.crbtcampaign.repository.UserLyriaHistoryRepository;
import com.platform.crbtcampaign.repository.UserSubscriptionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MusicGenerationServiceTest {

    @Mock AuthServiceClient authServiceClient;
    @Mock FileServiceClient fileServiceClient;
    @Mock LyriaClient lyriaClient;
    @Mock CreditWalletClient creditWalletClient;
    @Mock UserSubscriptionRepository subscriptionRepository;
    @Mock LyriaSystemPromptConfig promptConfig;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock RabbitTemplate rabbitTemplate;
    @Mock ObjectMapper objectMapper;
    @Mock UserLyriaHistoryRepository historyRepository;
    @Mock AudioGenerationClient audioGenerationClient;

    MusicGenerationService musicGenerationService;

    @BeforeEach
    void setUp() {
        musicGenerationService = new MusicGenerationService(
            authServiceClient,
            fileServiceClient,
            lyriaClient,
            creditWalletClient,
            subscriptionRepository,
            promptConfig,
            redisTemplate,
            rabbitTemplate,
            objectMapper,
            historyRepository,
            audioGenerationClient
        );
    }

    @Test
    void getMyLibrary_shouldMergeAndSortCorrectly() {
        Long userId = 42L;

        // Create mock AI music history
        Instant now = Instant.now();
        UserLyriaHistory ai1 = new UserLyriaHistory(userId, "0912345678", "Chill Pop Vibes", "Pop", "Chill", "Piano", "http://minio/ai1.mp3");
        ai1.setDeleted(false);
        // Injecting createdAt requires reflection or setting it via mock, but since JPA @CreationTimestamp populates it on insert,
        // we can assume a constructor sets it or we just mock/reflection set it for test context. Since there is no setter for createdAt,
        // let's check if the class sets it. Ah, `Instant.now()` is set inside the entity constructor. Wait, the constructor in our entity:
        // public UserLyriaHistory(...) doesn't set it, but let's check: yes, it sets durationSeconds and deleted, and @CreationTimestamp sets createdAt.
        // Let's use reflection to set createdAt on the mock entity to control sorting in the test.
        setCreatedAt(ai1, now.minus(10, ChronoUnit.MINUTES));

        UserLyriaHistory ai2 = new UserLyriaHistory(userId, "0912345678", "Happy Guitar Melody", "Guitar", "Happy", null, "http://minio/ai2.mp3");
        ai2.setDeleted(false);
        setCreatedAt(ai2, now.minus(5, ChronoUnit.MINUTES));

        when(historyRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId))
            .thenReturn(List.of(ai2, ai1)); // ai2 is newer

        // Create mock DIY jobs (one completed, one pending/failed)
        DiyJobResponse diyCompleted = new DiyJobResponse(
            100L,
            "Mix voice test prompt",
            "voice1",
            "COMPLETED",
            "http://minio/diy1.mp3",
            null,
            now.minus(2, ChronoUnit.MINUTES) // newer than ai2
        );
        DiyJobResponse diyPending = new DiyJobResponse(
            101L,
            "Pending job",
            "voice1",
            "PROCESSING",
            null,
            null,
            now
        );

        // Mock the Feign client. Mock request context first to provide Authorization header
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer test-token");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
            new org.springframework.web.context.request.ServletRequestAttributes(request)
        );

        when(audioGenerationClient.getUserJobs("Bearer test-token"))
            .thenReturn(ApiResponse.success(List.of(diyCompleted, diyPending)));

        List<MyLibraryItemResponse> library;
        try {
            // Execute
            library = musicGenerationService.getMyLibrary(userId);
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }

        // Assertions
        assertNotNull(library);
        assertEquals(3, library.size()); // 2 AI + 1 completed DIY

        // Verify sorting (newest first: diyCompleted -> ai2 -> ai1)
        assertEquals("DIY_100", library.get(0).id());
        assertEquals("AI_" + ai2.getId(), library.get(1).id());
        assertEquals("AI_" + ai1.getId(), library.get(2).id());

        // Verify tags mapping
        List<String> ai1Tags = library.get(2).tags();
        assertEquals(4, ai1Tags.size());
        assertEquals("pop", ai1Tags.get(0));
        assertEquals("chill", ai1Tags.get(1));
        assertEquals("piano", ai1Tags.get(2));
        assertEquals("45s", ai1Tags.get(3));

        List<String> diyTags = library.get(0).tags();
        assertEquals(2, diyTags.size());
        assertEquals("diy", diyTags.get(0));
        assertEquals("mixed", diyTags.get(1));

    }

    @Test
    void deleteLibraryItem_shouldSoftDeleteAiHistory() {
        Long userId = 42L;
        String unifiedId = "AI_10";

        UserLyriaHistory history = new UserLyriaHistory(userId, "0912345678", "Title", "Pop", "Chill", null, "url");
        when(historyRepository.findById(10L)).thenReturn(Optional.of(history));

        musicGenerationService.deleteLibraryItem(userId, unifiedId);

        verify(historyRepository).save(history);
        assertEquals(true, history.isDeleted());
    }

    private void setCreatedAt(UserLyriaHistory target, Instant time) {
        try {
            java.lang.reflect.Field field = UserLyriaHistory.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(target, time);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
