package com.platform.audiogeneration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.platform.audiogeneration.client.AiMediaWorkerClient;
import com.platform.audiogeneration.dto.request.GenerateAudioRequest;
import com.platform.audiogeneration.dto.response.AudioJobResponse;
import com.platform.audiogeneration.entity.AudioJob;
import com.platform.audiogeneration.entity.AudioJob.JobStatus;
import com.platform.audiogeneration.repository.AudioJobRepository;
import com.platform.common.core.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class AudioGenerationServiceTest {
    @Mock AudioJobRepository jobRepository;
    @Mock AiMediaWorkerClient aiClient;
    @Mock com.platform.audiogeneration.client.FileServiceClient fileServiceClient;
    @Mock RabbitTemplate rabbitTemplate;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock com.platform.audiogeneration.client.CreditWalletClient creditWalletClient;
    @InjectMocks AudioGenerationService service;

    @Test
    void submit_shouldRejectWhenLimitReached() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any(String.class), eq(1L))).thenReturn(6L);
        assertThrows(BaseException.class,
            () -> service.submitJob(1L, new GenerateAudioRequest("hello", "vi-VN-HoaiMyNeural", null, null, null, null)));
    }

    @Test
    void submit_shouldPersistPendingJob() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any(String.class), eq(1L))).thenReturn(1L);
        when(jobRepository.save(any(AudioJob.class))).thenAnswer(inv -> {
            AudioJob j = inv.getArgument(0);
            j.setStatus(AudioJob.JobStatus.PENDING);
            try {
                java.lang.reflect.Field f = AudioJob.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(j, 1L);
            } catch (Exception ignored) {}
            return j;
        });
        when(jobRepository.findById(any())).thenAnswer(inv -> {
            AudioJob j = new AudioJob(1L, "hello", "vi-VN-HoaiMyNeural");
            j.setStatus(AudioJob.JobStatus.PENDING);
            try {
                java.lang.reflect.Field f = AudioJob.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(j, 1L);
            } catch (Exception ignored) {}
            return Optional.of(j);
        });
        AudioJobResponse resp = service.submitJob(1L, new GenerateAudioRequest("hello", "vi-VN-HoaiMyNeural", null, null, null, null));
        assertEquals(JobStatus.PENDING, resp.status());
        assertEquals("hello", resp.prompt());
    }

    @Test
    void getJobProgress_shouldReturnProgressFromRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("audio_gen:job_progress:1")).thenReturn("PROCESSING");

        String progress = service.getJobProgress(1L);

        assertEquals("PROCESSING", progress);
    }

    @Test
    void submit_diyShouldRejectWhenInvalidPrompt() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any(String.class), eq(1L))).thenReturn(1L);

        // Prompt too long
        String longPrompt = "a".repeat(101);
        assertThrows(BaseException.class, () ->
            service.submitJob(1L, new GenerateAudioRequest(longPrompt, "voice", "DIY", "key", 0.0, 50.0))
        );

        // Non-ASCII prompt
        String nonAsciiPrompt = "xin chào";
        assertThrows(BaseException.class, () ->
            service.submitJob(1L, new GenerateAudioRequest(nonAsciiPrompt, "voice", "DIY", "key", 0.0, 50.0))
        );
    }

    @Test
    void submit_diyShouldRejectWhenInvalidDuration() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any(String.class), eq(1L))).thenReturn(1L);

        // End time <= start time
        assertThrows(BaseException.class, () ->
            service.submitJob(1L, new GenerateAudioRequest("hello", "voice", "DIY", "key", 40.0, 10.0))
        );

        // Start time negative
        assertThrows(BaseException.class, () ->
            service.submitJob(1L, new GenerateAudioRequest("hello", "voice", "DIY", "key", -5.0, 30.0))
        );
    }

    @Test
    void submit_diyShouldRejectWhenInsufficientCredit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any(String.class), eq(1L))).thenReturn(1L);
        when(creditWalletClient.getBalance(any())).thenReturn(
            new com.platform.common.core.response.ApiResponse<>(true, "SUCCESS", new com.platform.audiogeneration.client.dto.WalletResponse(1L, 0), System.currentTimeMillis())
        );

        assertThrows(BaseException.class, () ->
            service.submitJob(1L, new GenerateAudioRequest("hello", "voice", "DIY", "key", 0.0, 50.0))
        );
    }

    @Test
    void submit_diyShouldSucceedAndDeductCredit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any(String.class), eq(1L))).thenReturn(1L);
        when(creditWalletClient.getBalance(any())).thenReturn(
            new com.platform.common.core.response.ApiResponse<>(true, "SUCCESS", new com.platform.audiogeneration.client.dto.WalletResponse(1L, 5), System.currentTimeMillis())
        );
        when(creditWalletClient.deduct(eq(1L), any())).thenReturn(
            new com.platform.common.core.response.ApiResponse<>(true, "SUCCESS", new com.platform.audiogeneration.client.dto.WalletResponse(1L, 4), System.currentTimeMillis())
        );
        when(jobRepository.save(any(AudioJob.class))).thenAnswer(inv -> {
            AudioJob j = inv.getArgument(0);
            j.setStatus(AudioJob.JobStatus.PENDING);
            try {
                java.lang.reflect.Field f = AudioJob.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(j, 1L);
            } catch (Exception ignored) {}
            return j;
        });
        when(jobRepository.findById(any())).thenAnswer(inv -> {
            AudioJob j = new AudioJob(1L, "hello", "voice");
            j.setStatus(AudioJob.JobStatus.PENDING);
            j.setJobType("DIY");
            try {
                java.lang.reflect.Field f = AudioJob.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(j, 1L);
            } catch (Exception ignored) {}
            return Optional.of(j);
        });

        AudioJobResponse resp = service.submitJob(1L, new GenerateAudioRequest("hello", "voice", "DIY", "key", 0.0, 50.0));
        assertEquals(JobStatus.PENDING, resp.status());
    }

    @Test
    void analyzeAudio_shouldReturnFormattedSuggestions() {
        org.springframework.web.multipart.MultipartFile mockFile = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        
        java.util.Map<String, Object> rawResult = new java.util.HashMap<>();
        rawResult.put("confidence", 0.99);
        
        java.util.List<java.util.Map<String, Object>> proposals = new java.util.ArrayList<>();
        
        java.util.Map<String, Object> p1 = new java.util.HashMap<>();
        p1.put("start", 0.0);
        p1.put("end", 45.0);
        proposals.add(p1);
        
        java.util.Map<String, Object> p2 = new java.util.HashMap<>();
        p2.put("start", 20.0);
        p2.put("end", 65.0);
        proposals.add(p2);
        
        java.util.Map<String, Object> p3 = new java.util.HashMap<>();
        p3.put("start", 80.0);
        p3.put("end", 125.0);
        proposals.add(p3);
        
        rawResult.put("chorus_proposals", proposals);
        
        when(aiClient.detectChorus(mockFile)).thenReturn(rawResult);
        
        java.util.Map<String, Object> result = service.analyzeAudio(mockFile, true);
        
        java.util.List<?> suggestions = (java.util.List<?>) result.get("suggestions");
        assertEquals(3, suggestions.size());
        
        java.util.Map<?, ?> s1 = (java.util.Map<?, ?>) suggestions.get(0);
        assertEquals(1, s1.get("rank"));
        assertEquals(0.0, s1.get("start"));
        assertEquals(45.0, s1.get("end"));
        assertEquals(45.0, s1.get("duration"));
        assertEquals(0.99, s1.get("confidence"));
        
        java.util.Map<?, ?> s2 = (java.util.Map<?, ?>) suggestions.get(1);
        assertEquals(2, s2.get("rank"));
        assertEquals(20.0, s2.get("start"));
        assertEquals(65.0, s2.get("end"));
        assertEquals(45.0, s2.get("duration"));
        assertEquals(0.92, s2.get("confidence"));
        
        java.util.Map<?, ?> s3 = (java.util.Map<?, ?>) suggestions.get(2);
        assertEquals(3, s3.get("rank"));
        assertEquals(80.0, s3.get("start"));
        assertEquals(125.0, s3.get("end"));
        assertEquals(45.0, s3.get("duration"));
        assertEquals(0.88, s3.get("confidence"));
    }

    @Test
    void confirmAndValidateDiyAudio_shouldThrowExceptionWhenVocalDetected() throws Exception {
        Long fileId = 123L;
        String targetBucket = "media-audio-lib";

        when(fileServiceClient.confirmFile(eq(fileId), any())).thenReturn(
            new com.platform.common.core.response.ApiResponse<>(false, "Nhạc có lời không được phép sử dụng. Vui lòng tải lên nhạc không lời.", null, System.currentTimeMillis())
        );

        BaseException ex = assertThrows(BaseException.class, () ->
            service.confirmAndValidateDiyAudio(fileId, targetBucket)
        );
        assertEquals("Xác nhận file thất bại: Nhạc có lời không được phép sử dụng. Vui lòng tải lên nhạc không lời.", ex.getMessage());
    }

    @Test
    void confirmAndValidateDiyAudio_shouldSucceedWhenNoVocal() throws Exception {
        Long fileId = 123L;
        String targetBucket = "media-audio-lib";

        java.util.Map<String, Object> confirmData = new java.util.HashMap<>();
        confirmData.put("storedKey", "media-audio-lib/confirmed.mp3");
        when(fileServiceClient.confirmFile(eq(fileId), any())).thenReturn(
            new com.platform.common.core.response.ApiResponse<>(true, "SUCCESS", confirmData, System.currentTimeMillis())
        );

        java.util.Map<String, Object> result = service.confirmAndValidateDiyAudio(fileId, targetBucket);

        assertEquals(fileId, result.get("fileId"));
        assertEquals("media-audio-lib/confirmed.mp3", result.get("audioFileKey"));
    }
}
