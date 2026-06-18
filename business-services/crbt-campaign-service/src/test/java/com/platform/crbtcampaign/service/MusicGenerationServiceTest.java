package com.platform.crbtcampaign.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.ai.LyriaSystemPromptConfig;
import com.platform.common.core.response.ApiResponse;
import com.platform.common.core.response.PageResponse;
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
import com.platform.crbtcampaign.entity.UserSubscription;
import com.platform.crbtcampaign.client.dto.WalletResponse;
import com.platform.crbtcampaign.exception.CampaignErrorCode;
import com.platform.crbtcampaign.dto.response.GenerateMusicResponse;
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

        when(historyRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
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
            com.platform.common.core.response.PageResponse<MyLibraryItemResponse> res = musicGenerationService.getMyLibrary(userId, null, -1, 0, 10);
            library = res.content();
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
        assertEquals("30s", ai1Tags.get(3));

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

    @Test
    void searchMusicItemsAdmin_shouldFilterAndCombineCorrectly() {
        Instant now = Instant.now();
        UserLyriaHistory ai = new UserLyriaHistory(42L, "0912345678", "Chill Pop Vibes", "Pop", "Chill", "Piano", "http://minio/ai.mp3");
        setCreatedAt(ai, now.minus(5, ChronoUnit.MINUTES));

        org.springframework.data.domain.Page<UserLyriaHistory> pageResult = new org.springframework.data.domain.PageImpl<>(List.of(ai));
        when(historyRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(pageResult);
        when(historyRepository.count(any(org.springframework.data.jpa.domain.Specification.class)))
            .thenReturn(1L);

        DiyJobResponse diy = new DiyJobResponse(
            100L,
            "Mix voice test prompt",
            "voice1",
            "COMPLETED",
            "http://minio/diy.mp3",
            null,
            now.minus(2, ChronoUnit.MINUTES),
            "My DIY Title",
            "0912345678"
        );

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer test-token");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
            new org.springframework.web.context.request.ServletRequestAttributes(request)
        );

        try {
            when(audioGenerationClient.searchJobsAdmin(eq("Bearer test-token"), any(), any(), any(), any(), any(), eq(0), eq(10)))
                .thenReturn(ApiResponse.success(List.of(diy)));

            PageResponse<MyLibraryItemResponse> result = musicGenerationService.searchMusicItemsAdmin(
                now.minus(1, ChronoUnit.DAYS).toString(),
                now.plus(1, ChronoUnit.DAYS).toString(),
                null,
                null,
                "0912345678",
                "Pop",
                0,
                10
            );

            assertNotNull(result);
            assertEquals(2, result.content().size());
            assertEquals(2, result.totalElements());
            assertEquals(1, result.totalPages());
            assertEquals(0, result.page());
            assertEquals(10, result.size());
            assertEquals("DIY_100", result.content().get(0).id());
            assertEquals("My DIY Title", result.content().get(0).title());
            assertEquals("AI_" + ai.getId(), result.content().get(1).id());
            assertEquals("Chill Pop Vibes", result.content().get(1).title());
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void deleteMusicItemAdmin_shouldInvokeCorrectRepositoryOrClient() {
        UserLyriaHistory ai = new UserLyriaHistory(42L, "0912345678", "Chill Pop Vibes", "Pop", "Chill", "Piano", "http://minio/ai.mp3");
        when(historyRepository.findById(10L)).thenReturn(Optional.of(ai));

        musicGenerationService.deleteMusicItemAdmin("AI_10", false);
        verify(historyRepository, times(1)).save(ai);
        assertEquals(true, ai.isDeleted());

        musicGenerationService.deleteMusicItemAdmin("AI_10", true);
        verify(historyRepository, times(1)).delete(ai);

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer test-token");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
            new org.springframework.web.context.request.ServletRequestAttributes(request)
        );

        try {
            when(audioGenerationClient.deleteJobAdmin("Bearer test-token", 100L, false))
                .thenReturn(ApiResponse.success(null));

            musicGenerationService.deleteMusicItemAdmin("DIY_100", false);
            verify(audioGenerationClient, times(1)).deleteJobAdmin("Bearer test-token", 100L, false);
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void updateLibraryItem_shouldUpdateAiHistory_whenOwnerMatches() {
        Long userId = 42L;
        String unifiedId = "AI_10";
        MyLibraryItemResponse req = new MyLibraryItemResponse(null, "New AI Title", null, null, null, null);

        UserLyriaHistory history = new UserLyriaHistory(userId, "0912345678", "Old Title", "Pop", "Chill", "Piano", "url");
        when(historyRepository.findById(10L)).thenReturn(Optional.of(history));

        MyLibraryItemResponse resp = musicGenerationService.updateLibraryItem(userId, unifiedId, req);

        assertNotNull(resp);
        assertEquals("New AI Title", resp.title());
        assertEquals("New AI Title", history.getTitle());
        verify(historyRepository, times(1)).save(history);
    }

    @Test
    void updateLibraryItem_shouldThrowForbidden_whenOwnerDoesNotMatchForAi() {
        Long ownerId = 42L;
        Long otherUserId = 99L;
        String unifiedId = "AI_10";
        MyLibraryItemResponse req = new MyLibraryItemResponse(null, "New AI Title", null, null, null, null);

        UserLyriaHistory history = new UserLyriaHistory(ownerId, "0912345678", "Old Title", "Pop", "Chill", "Piano", "url");
        when(historyRepository.findById(10L)).thenReturn(Optional.of(history));

        org.junit.jupiter.api.Assertions.assertThrows(com.platform.common.core.exception.BaseException.class, () -> {
            musicGenerationService.updateLibraryItem(otherUserId, unifiedId, req);
        });
    }

    @Test
    void updateLibraryItem_shouldInvokeClientUpdate_whenDiy() {
        Long userId = 42L;
        String unifiedId = "DIY_100";
        MyLibraryItemResponse req = new MyLibraryItemResponse(null, "New DIY Title", null, null, null, null);

        DiyJobResponse clientResp = new DiyJobResponse(
            100L, "prompt", "voice", "COMPLETED", "http://minio/diy1.mp3", null, Instant.now(), "New DIY Title", "0912345678"
        );

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer test-token");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
            new org.springframework.web.context.request.ServletRequestAttributes(request)
        );

        try {
            when(audioGenerationClient.updateJob(eq("Bearer test-token"), eq(100L), any(DiyJobResponse.class)))
                .thenReturn(ApiResponse.success(clientResp));

            MyLibraryItemResponse resp = musicGenerationService.updateLibraryItem(userId, unifiedId, req);

            assertNotNull(resp);
            assertEquals("New DIY Title", resp.title());
            verify(audioGenerationClient, times(1)).updateJob(eq("Bearer test-token"), eq(100L), any(DiyJobResponse.class));
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void generate_shouldRetryOnContentFilteredAndSucceed() {
        String msisdn = "09673259486";
        Long userId = 3L;
        String genre = "pop";
        String mood = "energize";
        String instrument = "saxophone";

        com.platform.crbtcampaign.client.dto.UserCreditResponse userCredit =
            new com.platform.crbtcampaign.client.dto.UserCreditResponse(userId, "username");
        when(authServiceClient.getUserCredit(msisdn)).thenReturn(userCredit);

        UserSubscription sub = mock(UserSubscription.class);
        when(sub.getExpiresAt()).thenReturn(Instant.now().plusSeconds(3600));
        when(subscriptionRepository.findByUserIdAndStatus(userId, UserSubscription.Status.ACTIVE))
            .thenReturn(List.of(sub));

        WalletResponse wallet = new WalletResponse(userId, 10);
        when(creditWalletClient.getBalance(userId)).thenReturn(ApiResponse.success(wallet));
        when(creditWalletClient.deduct(eq(userId), any())).thenReturn(ApiResponse.success(wallet));

        org.springframework.data.redis.core.ListOperations<String, String> listOps = mock(org.springframework.data.redis.core.ListOperations.class);
        org.springframework.data.redis.core.SetOperations<String, String> setOps = mock(org.springframework.data.redis.core.SetOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(listOps.range(any(), eq(0L), eq(-1L))).thenReturn(List.of());
        when(setOps.members(any())).thenReturn(null);

        when(promptConfig.buildPrompt(any(), any(), any(), any())).thenReturn("Mock Prompt");
        
        // Mock LyriaClient behavior: 1st call throws filtered exception, 2nd call succeeds
        when(lyriaClient.generateMusic(any(), anyLong()))
            .thenThrow(new LyriaClient.LyriaContentFilteredException("Filtered"))
            .thenReturn(new byte[]{1, 2, 3});

        when(fileServiceClient.uploadAudio(any(), any())).thenReturn(ApiResponse.success("http://minio/success.mp3"));

        GenerateMusicResponse resp = musicGenerationService.generate(msisdn, genre, mood, instrument);

        assertNotNull(resp);
        assertEquals("http://minio/success.mp3", resp.url());
        verify(lyriaClient, times(2)).generateMusic(any(), anyLong());
    }

    @Test
    void generate_shouldFailWhenMaxRetriesExceeded() {
        String msisdn = "09673259486";
        Long userId = 3L;
        String genre = "pop";
        String mood = "energize";
        String instrument = "saxophone";

        com.platform.crbtcampaign.client.dto.UserCreditResponse userCredit =
            new com.platform.crbtcampaign.client.dto.UserCreditResponse(userId, "username");
        when(authServiceClient.getUserCredit(msisdn)).thenReturn(userCredit);

        UserSubscription sub = mock(UserSubscription.class);
        when(sub.getExpiresAt()).thenReturn(Instant.now().plusSeconds(3600));
        when(subscriptionRepository.findByUserIdAndStatus(userId, UserSubscription.Status.ACTIVE))
            .thenReturn(List.of(sub));

        WalletResponse wallet = new WalletResponse(userId, 10);
        when(creditWalletClient.getBalance(userId)).thenReturn(ApiResponse.success(wallet));
        when(creditWalletClient.deduct(eq(userId), any())).thenReturn(ApiResponse.success(wallet));

        org.springframework.data.redis.core.ListOperations<String, String> listOps = mock(org.springframework.data.redis.core.ListOperations.class);
        org.springframework.data.redis.core.SetOperations<String, String> setOps = mock(org.springframework.data.redis.core.SetOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(listOps.range(any(), eq(0L), eq(-1L))).thenReturn(List.of());
        when(setOps.members(any())).thenReturn(null);

        when(promptConfig.buildPrompt(any(), any(), any(), any())).thenReturn("Mock Prompt");

        // Mock LyriaClient behavior: all 3 attempts throw filtered exception
        when(lyriaClient.generateMusic(any(), anyLong()))
            .thenThrow(new LyriaClient.LyriaContentFilteredException("Filtered"));

        com.platform.common.core.exception.BaseException ex = org.junit.jupiter.api.Assertions.assertThrows(
            com.platform.common.core.exception.BaseException.class,
            () -> musicGenerationService.generate(msisdn, genre, mood, instrument)
        );

        assertEquals(CampaignErrorCode.LYRIA_GENERATION_FILTERED, ex.getErrorCode());
        verify(lyriaClient, times(3)).generateMusic(any(), anyLong());
        // Verify refund is executed on failure
        verify(creditWalletClient, times(1)).add(eq(userId), any());
    }

    @Test
    void createMusicItemAdmin_shouldParseDurationFromTags() {
        MyLibraryItemResponse req = new MyLibraryItemResponse(
            null,
            "Chill Pop Vibes",
            "AI",
            List.of("pop", "chill", "piano", "45s"),
            "http://minio/ai.mp3",
            null,
            "0912345678"
        );

        when(historyRepository.save(any(UserLyriaHistory.class))).thenAnswer(invocation -> {
            UserLyriaHistory item = invocation.getArgument(0);
            // Verify duration was parsed correctly from "45s"
            assertEquals(45, item.getDurationSeconds());
            return item;
        });

        MyLibraryItemResponse result = musicGenerationService.createMusicItemAdmin(req);
        org.junit.jupiter.api.Assertions.assertNotNull(result);
        org.junit.jupiter.api.Assertions.assertTrue(result.tags().contains("45s"));
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
