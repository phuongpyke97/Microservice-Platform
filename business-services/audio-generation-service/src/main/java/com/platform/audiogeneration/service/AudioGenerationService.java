package com.platform.audiogeneration.service;

import com.platform.audiogeneration.client.AiMediaWorkerClient;
import com.platform.audiogeneration.client.FileServiceClient;
import com.platform.audiogeneration.client.ByteArrayMultipartFile;
import com.platform.audiogeneration.client.TempFileMultipartFile;
import com.platform.audiogeneration.client.CreditWalletClient;
import com.platform.audiogeneration.client.dto.WalletAmountRequest;
import com.platform.audiogeneration.client.dto.WalletResponse;
import com.platform.audiogeneration.exception.AudioGenerationErrorCode;
import com.platform.common.core.response.ApiResponse;
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
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final FileServiceClient fileServiceClient;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;
    private final CreditWalletClient creditWalletClient;

    public AudioGenerationService(AudioJobRepository jobRepository,
                                  AiMediaWorkerClient aiClient,
                                  FileServiceClient fileServiceClient,
                                  RabbitTemplate rabbitTemplate,
                                  StringRedisTemplate redisTemplate,
                                  CreditWalletClient creditWalletClient) {
        this.jobRepository = jobRepository;
        this.aiClient = aiClient;
        this.fileServiceClient = fileServiceClient;
        this.rabbitTemplate = rabbitTemplate;
        this.redisTemplate = redisTemplate;
        this.creditWalletClient = creditWalletClient;
    }

    public Map<String, Object> analyzeAudio(org.springframework.web.multipart.MultipartFile file) {
        return analyzeAudio(file, false);
    }

    public Map<String, Object> analyzeAudio(org.springframework.web.multipart.MultipartFile file, boolean skipVocal) {
        if (!skipVocal) {
            // Step 1: Detect vocal presence first using separate-audio
            Map<String, String> separation = aiClient.separateAudio(file);
            Object hasVocalObj = separation.get("has_vocal");
            boolean hasVocal = false;
            if (hasVocalObj instanceof Boolean) {
                hasVocal = (Boolean) hasVocalObj;
            } else if (hasVocalObj instanceof String) {
                hasVocal = Boolean.parseBoolean((String) hasVocalObj);
            }

            if (hasVocal) {
                throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Nhạc có lời không được phép sử dụng. Vui lòng tải lên nhạc không lời.");
            }
        }

        // Step 2: Extract chorus timestamps
        return aiClient.detectChorus(file);
    }

    public Map<String, Object> analyzeAudioFromKey(String audioFileKey) {
        File tempFile = null;
        try {
            Long fileId = Long.parseLong(audioFileKey);
            ApiResponse<Map<String, Object>> downloadUrlResp = fileServiceClient.getDownloadUrl(fileId);
            if (downloadUrlResp == null || downloadUrlResp.data() == null) {
                throw new RuntimeException("Failed to get download URL from file service");
            }
            String downloadUrl = (String) downloadUrlResp.data().get("url");

            tempFile = File.createTempFile("lib-bg-", ".mp3");
            try (java.io.InputStream in = new java.net.URL(downloadUrl).openStream()) {
                Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            org.springframework.web.multipart.MultipartFile filePart =
                new TempFileMultipartFile(tempFile, "file", "music.mp3", "audio/mpeg");

            // Flow B: Skip vocal detection (skipVocal = true)
            return analyzeAudio(filePart, true);
        } catch (Exception e) {
            log.error("Failed to analyze audio from key {}", audioFileKey, e);
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Không thể tải và phân tích file nhạc từ kho hệ thống: " + e.getMessage());
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    @Transactional
    public AudioJobResponse submitJob(Long userId, GenerateAudioRequest request) {
        long active = redisTemplate.opsForValue().increment(ACTIVE_JOBS_KEY + userId, 1);
        if (active > MAX_ACTIVE_JOBS) {
            redisTemplate.opsForValue().decrement(ACTIVE_JOBS_KEY + userId);
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Max " + MAX_ACTIVE_JOBS + " active jobs");
        }

        // Validate DIY specific constraints
        if ("DIY".equalsIgnoreCase(request.type())) {
            // BR-02-01: Limit prompt to 100 characters and English only (ASCII)
            if (request.prompt() == null || request.prompt().length() > 100 || !request.prompt().matches("^[\\p{ASCII}]+$")) {
                redisTemplate.opsForValue().decrement(ACTIVE_JOBS_KEY + userId);
                throw new BaseException(AudioGenerationErrorCode.INVALID_PROMPT);
            }

            // BR-1A-05 / BR-1B-04: Clip duration must be between 40 and 60 seconds
            if (request.vocalStart() == null || request.vocalEnd() == null) {
                redisTemplate.opsForValue().decrement(ACTIVE_JOBS_KEY + userId);
                throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Missing vocal start or end time");
            }
            double duration = request.vocalEnd() - request.vocalStart();
            if (duration < 40.0 || duration > 60.0) {
                redisTemplate.opsForValue().decrement(ACTIVE_JOBS_KEY + userId);
                throw new BaseException(AudioGenerationErrorCode.INVALID_CLIP_DURATION);
            }
        }

        AudioJob job = new AudioJob(userId, request.prompt(), request.voiceId());
        if (request.type() != null) {
            job.setJobType(request.type());
        }
        job.setAudioFileKey(request.audioFileKey());
        job.setVocalStart(request.vocalStart());
        job.setVocalEnd(request.vocalEnd());

        // 1. Save job to database first. If this fails, no credit is deducted.
        jobRepository.save(job);

        // 2. Check and deduct credit. If this throws an exception, the transaction rolls back, deleting the job record.
        if ("DIY".equalsIgnoreCase(request.type())) {
            try {
                // BR-03-01: Check balance and deduct credit
                ApiResponse<WalletResponse> balanceResp = creditWalletClient.getBalance(userId);
                if (balanceResp == null || !balanceResp.success() || balanceResp.data() == null) {
                    throw new BaseException(AudioGenerationErrorCode.WALLET_INSUFFICIENT_CREDIT);
                }
                if (balanceResp.data().balance() < 1) {
                    throw new BaseException(AudioGenerationErrorCode.WALLET_INSUFFICIENT_CREDIT);
                }

                ApiResponse<WalletResponse> deductResp = creditWalletClient.deduct(userId, new WalletAmountRequest(
                    1, "DIY Ringback Tone generation", "DIY-GEN-" + job.getId()
                ));
                if (deductResp == null || !deductResp.success() || deductResp.data() == null) {
                    throw new BaseException(AudioGenerationErrorCode.WALLET_INSUFFICIENT_CREDIT);
                }
            } catch (Exception e) {
                redisTemplate.opsForValue().decrement(ACTIVE_JOBS_KEY + userId);
                throw e;
            }
        }

        processJobAsync(job.getId());
        return toResponse(job);
    }


    @Async("audioJobExecutor")
    public void processJobAsync(Long jobId) {
        AudioJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));
        try {
            job.setStatus(JobStatus.PROCESSING);
            jobRepository.save(job);
            updateProgress(job.getId(), "Starting audio generation...");

            String finalUrl;
            if ("DIY".equalsIgnoreCase(job.getJobType())) {
                if (job.getAudioFileKey() == null) {
                    throw new RuntimeException("Missing audio file key for DIY job");
                }

                updateProgress(job.getId(), "Fetching background music from storage...");
                Long fileId = Long.parseLong(job.getAudioFileKey());
                ApiResponse<Map<String, Object>> downloadUrlResp = fileServiceClient.getDownloadUrl(fileId);
                if (downloadUrlResp == null || downloadUrlResp.data() == null) {
                    throw new RuntimeException("Failed to get download URL from file service");
                }
                String downloadUrl = (String) downloadUrlResp.data().get("url");

                File tempBgFile = File.createTempFile("bg-diy-", ".mp3");
                try {
                    try (java.io.InputStream in = new java.net.URL(downloadUrl).openStream()) {
                        Files.copy(in, tempBgFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }

                    updateProgress(job.getId(), "Generating voiceover via Text-to-Speech...");
                    byte[] voiceBytes = aiClient.generateTts(Map.of(
                        "text", job.getPrompt(),
                        "voice", job.getVoiceId() != null ? job.getVoiceId() : "en-US-JennyNeural",
                        "output_format", "audio-24khz-48kbitrate-mono-mp3"
                    ));

                    updateProgress(job.getId(), "Mixing voice and background tracks...");
                    org.springframework.web.multipart.MultipartFile vocalPart =
                        new ByteArrayMultipartFile(voiceBytes, "vocal", "voice.mp3", "audio/mpeg");
                    org.springframework.web.multipart.MultipartFile bgPart =
                        new TempFileMultipartFile(tempBgFile, "accompaniment", "accompaniment.wav", "audio/wav");

                    byte[] mixV1 = aiClient.mixAudio(vocalPart, bgPart, "v1");
                    byte[] mixV2 = aiClient.mixAudio(vocalPart, bgPart, "v2");
                    byte[] mixV3 = aiClient.mixAudio(vocalPart, bgPart, "v3");

                    updateProgress(job.getId(), "Uploading mixed versions to storage...");
                    ApiResponse<String> uploadV1 = fileServiceClient.uploadAudioBytes(mixV1, "media-audio");
                    ApiResponse<String> uploadV2 = fileServiceClient.uploadAudioBytes(mixV2, "media-audio");
                    ApiResponse<String> uploadV3 = fileServiceClient.uploadAudioBytes(mixV3, "media-audio");

                    if (uploadV1 == null || uploadV1.data() == null ||
                        uploadV2 == null || uploadV2.data() == null ||
                        uploadV3 == null || uploadV3.data() == null) {
                        throw new RuntimeException("Failed to upload mixed versions to file service");
                    }

                    finalUrl = uploadV1.data() + "," + uploadV2.data() + "," + uploadV3.data();
                } finally {
                    if (tempBgFile.exists()) {
                        tempBgFile.delete();
                    }
                }
            } else {
                byte[] audioBytes = aiClient.generateTts(Map.of(
                    "text", job.getPrompt(),
                    "voice", job.getVoiceId() != null ? job.getVoiceId() : "vi-VN-HoaiMyNeural",
                    "output_format", "audio-24khz-48kbitrate-mono-mp3"
                ));
                updateProgress(job.getId(), "TTS generation completed.");

                ApiResponse<String> uploadResp = fileServiceClient.uploadAudioBytes(audioBytes, "media-audio");
                if (uploadResp == null || uploadResp.data() == null) {
                    throw new RuntimeException("Failed to upload TTS audio to file service");
                }
                finalUrl = uploadResp.data();
            }

            job.setResultUrl(finalUrl);
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

            if ("DIY".equalsIgnoreCase(job.getJobType())) {
                try {
                    creditWalletClient.add(job.getUserId(), new WalletAmountRequest(
                        1,
                        "Refund: DIY Ringback Tone generation failed",
                        "DIY-REFUND-" + job.getId()
                    ));
                    log.info("Successfully refunded 1 credit to user {} for failed DIY job {}", job.getUserId(), job.getId());
                } catch (Exception ex) {
                    log.error("Failed to refund credit to user {} for failed DIY job {}", job.getUserId(), job.getId(), ex);
                }
            }

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
