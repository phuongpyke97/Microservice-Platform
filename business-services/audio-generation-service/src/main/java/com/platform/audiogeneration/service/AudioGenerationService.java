package com.platform.audiogeneration.service;

import com.platform.audiogeneration.client.AiMediaWorkerClient;
import com.platform.audiogeneration.dto.request.GenerateAudioRequest;
import com.platform.audiogeneration.dto.response.AudioJobResponse;
import com.platform.audiogeneration.entity.AudioJob;
import com.platform.audiogeneration.entity.AudioJob.JobStatus;
import com.platform.audiogeneration.repository.AudioJobRepository;
import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.common.rmq.RmqExchanges;
import com.platform.common.rmq.RmqRoutingKeys;
import com.platform.common.rmq.event.AudioGeneratedEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AudioGenerationService {
    private static final Logger log = LoggerFactory.getLogger(AudioGenerationService.class);
    private static final String ACTIVE_JOBS_KEY = "audio_gen:active_jobs:user:";
    private static final String JOB_PROGRESS_KEY = "audio_gen:job_progress:";
    private static final int MAX_ACTIVE_JOBS = 5;

    private final AudioJobRepository jobRepository;
    private final AiMediaWorkerClient aiClient;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;

    public AudioGenerationService(AudioJobRepository jobRepository,
                                  AiMediaWorkerClient aiClient,
                                  RabbitTemplate rabbitTemplate,
                                  StringRedisTemplate redisTemplate) {
        this.jobRepository = jobRepository;
        this.aiClient = aiClient;
        this.rabbitTemplate = rabbitTemplate;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public AudioJobResponse submitJob(Long userId, GenerateAudioRequest request) {
        long active = redisTemplate.opsForValue().increment(ACTIVE_JOBS_KEY + userId, 1);
        if (active > MAX_ACTIVE_JOBS) {
            redisTemplate.opsForValue().decrement(ACTIVE_JOBS_KEY + userId);
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Max " + MAX_ACTIVE_JOBS + " active jobs");
        }
        AudioJob job = new AudioJob(userId, request.prompt(), request.voiceId());
        jobRepository.save(job);
        processJobAsync(job.getId());
        return toResponse(job);
    }

    @Async
    public void processJobAsync(Long jobId) {
        AudioJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));
        try {
            job.setStatus(JobStatus.PROCESSING);
            jobRepository.save(job);
            updateProgress(job.getId(), "Starting audio generation...");

            // T9.5 DIY Flow: Chorus + Separate + TTS
            // Placeholder for actual audio data from request (not present in current DTO)
            // For now, only TTS is implemented, full DIY flow requires audio upload in request
            byte[] audioBytes = aiClient.generateTts(Map.of(
                "text", job.getPrompt(),
                "voice", job.getVoiceId() != null ? job.getVoiceId() : "vi-VN-HoaiMyNeural",
                "output_format", "audio-24khz-48kbitrate-mono-mp3"
            ));
            updateProgress(job.getId(), "TTS generation completed.");

            // Simulate MinIO upload and get URL
            String url = "minio://audio-bucket/" + job.getId() + ".mp3";
            job.setResultUrl(url);
            job.setStatus(JobStatus.COMPLETED);
            jobRepository.save(job);

            publishEvent(job);
            redisTemplate.opsForValue().decrement(ACTIVE_JOBS_KEY + job.getUserId());
            redisTemplate.delete(JOB_PROGRESS_KEY + job.getId());
        } catch (Exception e) {
            log.error("Job {} failed", jobId, e);
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            jobRepository.save(job);
            redisTemplate.opsForValue().decrement(ACTIVE_JOBS_KEY + job.getUserId());
            redisTemplate.delete(JOB_PROGRESS_KEY + job.getId());
        }
    }

    public List<AudioJobResponse> getUserJobs(Long userId) {
        return jobRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(this::toResponse).toList();
    }

    public AudioJobResponse getJob(Long jobId, Long userId) {
        AudioJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));
        if (!job.getUserId().equals(userId)) {
            throw new BaseException(CommonErrorCode.COMMON_FORBIDDEN);
        }
        return toResponse(job);
    }

    public String getJobProgress(Long jobId) {
        return redisTemplate.opsForValue().get(JOB_PROGRESS_KEY + jobId);
    }

    private void updateProgress(Long jobId, String message) {
        redisTemplate.opsForValue().set(JOB_PROGRESS_KEY + jobId, message, 10, TimeUnit.MINUTES);
    }

    private void publishEvent(AudioJob job) {
        AudioGeneratedEvent event = new AudioGeneratedEvent(
            job.getUserId(), job.getId().toString(), job.getResultUrl(),
            job.getStatus().name(), System.currentTimeMillis());
        rabbitTemplate.convertAndSend(RmqExchanges.AUDIO_EVENTS, RmqRoutingKeys.AUDIO_GENERATED, event);
    }

    private AudioJobResponse toResponse(AudioJob job) {
        return new AudioJobResponse(job.getId(), job.getPrompt(), job.getVoiceId(),
            job.getStatus(), job.getResultUrl(), job.getErrorMessage(), job.getCreatedAt());
    }
}
