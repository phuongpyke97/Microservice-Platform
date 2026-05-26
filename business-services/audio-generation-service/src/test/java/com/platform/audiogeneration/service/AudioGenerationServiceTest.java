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

        // Duration too short (30s)
        assertThrows(BaseException.class, () ->
            service.submitJob(1L, new GenerateAudioRequest("hello", "voice", "DIY", "key", 10.0, 40.0))
        );

        // Duration too long (70s)
        assertThrows(BaseException.class, () ->
            service.submitJob(1L, new GenerateAudioRequest("hello", "voice", "DIY", "key", 0.0, 70.0))
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
}
