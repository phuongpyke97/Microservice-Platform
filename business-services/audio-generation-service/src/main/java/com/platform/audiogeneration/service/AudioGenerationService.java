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
import java.io.InputStream;
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
        long t0 = System.currentTimeMillis();
        if (!skipVocal) {
            long tSep = System.currentTimeMillis();
            // Step 1: Detect vocal presence first using separate-audio
            Map<String, Object> separation = aiClient.separateAudio(file, true);
            log.info("[PERF] separateAudio took {} ms", System.currentTimeMillis() - tSep);
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
        long tChorus = System.currentTimeMillis();
        Map<String, Object> rawResult = aiClient.detectChorus(file);
        log.info("[PERF] detectChorus took {} ms", System.currentTimeMillis() - tChorus);
        Map<String, Object> formatted = formatAnalysisResult(rawResult);
        log.info("[PERF] total analyzeAudio took {} ms", System.currentTimeMillis() - t0);
        return formatted;
    }

    private Map<String, Object> formatAnalysisResult(Map<String, Object> rawResult) {
        Map<String, Object> formatted = new java.util.HashMap<>();
        List<Map<String, Object>> suggestions = new java.util.ArrayList<>();
        
        Object proposalsObj = rawResult.get("chorus_proposals");
        if (proposalsObj instanceof List) {
            List<?> proposalsList = (List<?>) proposalsObj;
            double overallConfidence = 0.99;
            Object confidenceObj = rawResult.get("confidence");
            if (confidenceObj instanceof Number) {
                overallConfidence = ((Number) confidenceObj).doubleValue();
            }
            
            for (int i = 0; i < proposalsList.size(); i++) {
                Object item = proposalsList.get(i);
                if (item instanceof Map) {
                    Map<?, ?> proposal = (Map<?, ?>) item;
                    double start = 0.0;
                    double end = 0.0;
                    if (proposal.get("start") instanceof Number) {
                        start = ((Number) proposal.get("start")).doubleValue();
                    }
                    if (proposal.get("end") instanceof Number) {
                        end = ((Number) proposal.get("end")).doubleValue();
                    }
                    
                    double duration = Math.round((end - start) * 10.0) / 10.0;
                    
                    double confidenceFactor = 1.0;
                    if (i == 1) {
                        confidenceFactor = 0.93;
                    } else if (i == 2) {
                        confidenceFactor = 0.89;
                    }
                    double confidence = Math.round(overallConfidence * confidenceFactor * 100.0) / 100.0;
                    
                    Map<String, Object> suggestion = new java.util.HashMap<>();
                    suggestion.put("rank", i + 1);
                    suggestion.put("start", start);
                    suggestion.put("end", end);
                    suggestion.put("duration", duration);
                    suggestion.put("confidence", confidence);
                    suggestions.add(suggestion);
                }
            }
        }
        
        if (suggestions.isEmpty()) {
            double start = 0.0;
            double end = 45.0;
            double confidence = 0.99;
            
            if (rawResult.get("start_time") instanceof Number) {
                start = ((Number) rawResult.get("start_time")).doubleValue();
            }
            if (rawResult.get("end_time") instanceof Number) {
                end = ((Number) rawResult.get("end_time")).doubleValue();
            }
            if (rawResult.get("confidence") instanceof Number) {
                confidence = ((Number) rawResult.get("confidence")).doubleValue();
            }
            
            double duration = Math.round((end - start) * 10.0) / 10.0;
            
            Map<String, Object> suggestion = new java.util.HashMap<>();
            suggestion.put("rank", 1);
            suggestion.put("start", start);
            suggestion.put("end", end);
            suggestion.put("duration", duration);
            suggestion.put("confidence", confidence);
            suggestions.add(suggestion);
        }
        
        formatted.put("suggestions", suggestions);
        return formatted;
    }

    public Map<String, Object> analyzeAudioFromKey(String audioFileKey, boolean skipVocal) {
        long t0 = System.currentTimeMillis();
        File tempFile = null;
        try {
            Long fileId = Long.parseLong(audioFileKey);
            long tDownload = System.currentTimeMillis();
            byte[] fileBytes;
            try (feign.Response response = fileServiceClient.downloadFile(fileId)) {
                if (response.status() >= 400) {
                    throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "File service returned error status: " + response.status());
                }
                if (response.body() == null) {
                    throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "File service response body is null");
                }
                try (InputStream is = response.body().asInputStream()) {
                    fileBytes = is.readAllBytes();
                }
            }
            log.info("[PERF] downloadFile took {} ms", System.currentTimeMillis() - tDownload);

            tempFile = File.createTempFile("lib-bg-", ".mp3");
            Files.write(tempFile.toPath(), fileBytes);

            org.springframework.web.multipart.MultipartFile filePart =
                new TempFileMultipartFile(tempFile, "file", "music.mp3", "audio/mpeg");

            long tAnalyze = System.currentTimeMillis();
            Map<String, Object> result = analyzeAudio(filePart, skipVocal);
            log.info("[PERF] Inner analyzeAudio took {} ms", System.currentTimeMillis() - tAnalyze);
            result.put("audioFileKey", audioFileKey);
            log.info("[PERF] total analyzeAudioFromKey took {} ms", System.currentTimeMillis() - t0);
            return result;
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
    public Map<String, Object> confirmAndValidateDiyAudio(Long fileId, String targetBucket) {
        // Call file-service to validate (duration, vocal presence, size) and move the file
        ApiResponse<Map<String, Object>> confirmResp = fileServiceClient.confirmFile(fileId, Map.of("targetBucket", targetBucket));
        if (confirmResp == null || !confirmResp.success() || confirmResp.data() == null) {
            String errorMsg = (confirmResp != null && confirmResp.message() != null) ? confirmResp.message() : "Unknown error";
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Xác nhận file thất bại: " + errorMsg);
        }

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("fileId", fileId);
        Map<String, Object> confirmData = confirmResp.data();
        if (confirmData.containsKey("storedKey")) {
            result.put("audioFileKey", confirmData.get("storedKey"));
        } else if (confirmData.containsKey("id")) {
            result.put("audioFileKey", confirmData.get("id").toString());
        } else {
            result.put("audioFileKey", fileId.toString());
        }
        
        return result;
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
            // BR-02-01: Limit prompt to 100 characters. Language depends on voice locale:
            //  - Burmese voice (my-MM-*) -> Burmese text allowed
            //  - Otherwise (English voice) -> ASCII only
            String prompt = request.prompt();
            String voiceId = request.voiceId();
            boolean burmeseVoice = voiceId != null && voiceId.startsWith("my-MM");
            boolean invalidLength = prompt == null || prompt.length() > 100;
            boolean invalidCharset = !burmeseVoice
                && (prompt == null || !prompt.matches("^[\\p{ASCII}]+$"));
            if (invalidLength || invalidCharset) {
                redisTemplate.opsForValue().decrement(ACTIVE_JOBS_KEY + userId);
                throw new BaseException(AudioGenerationErrorCode.INVALID_PROMPT);
            }

            // Validate that the start and end times are correct
            if (request.vocalStart() == null || request.vocalEnd() == null) {
                redisTemplate.opsForValue().decrement(ACTIVE_JOBS_KEY + userId);
                throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Missing vocal start or end time");
            }
            if (request.vocalStart() < 0.0 || request.vocalEnd() <= request.vocalStart()) {
                redisTemplate.opsForValue().decrement(ACTIVE_JOBS_KEY + userId);
                throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Invalid start or end time range");
            }
        }

        AudioJob job = new AudioJob(userId, request.prompt(), request.voiceId());
        if (request.type() != null) {
            job.setJobType(request.type());
        }
        job.setAudioFileKey(request.audioFileKey());
        job.setVocalStart(request.vocalStart());
        job.setVocalEnd(request.vocalEnd());

        String msisdn = com.platform.common.security.SecurityUtils.getCurrentMsisdn();
        if (msisdn == null || msisdn.isBlank()) {
            msisdn = request.msisdn();
        }
        job.setMsisdn(msisdn);

        String title = request.title();
        if (title == null || title.isBlank()) {
            title = "DIY Ringback Tone";
            if (request.prompt() != null && !request.prompt().isBlank()) {
                String cleaned = request.prompt().trim();
                title = cleaned.length() > 35 ? cleaned.substring(0, 32) + "..." : cleaned;
            }
        }
        job.setTitle(title);

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
                byte[] bgBytes;
                try (feign.Response response = fileServiceClient.downloadFile(fileId)) {
                    if (response.status() >= 400) {
                        throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "File service returned error status: " + response.status());
                    }
                    if (response.body() == null) {
                        throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "File service response body is null");
                    }
                    try (InputStream is = response.body().asInputStream()) {
                        bgBytes = is.readAllBytes();
                    }
                }

                File tempBgFile = File.createTempFile("bg-diy-", ".mp3");
                try {
                    Files.write(tempBgFile.toPath(), bgBytes);

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

                    Double vocalStart = job.getVocalStart() != null ? job.getVocalStart() : 0.0;
                    Double vocalEnd = job.getVocalEnd() != null ? job.getVocalEnd() : 0.0;

                    byte[] mixV1 = aiClient.mixAudio(vocalPart, bgPart, "v1", vocalStart, vocalEnd);
                    byte[] mixV2 = aiClient.mixAudio(vocalPart, bgPart, "v2", vocalStart, vocalEnd);
                    byte[] mixV3 = aiClient.mixAudio(vocalPart, bgPart, "v3", vocalStart, vocalEnd);

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
        return jobRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId).stream()
            .map(this::toResponse).toList();
    }

    public AudioJobResponse getJob(Long jobId, Long userId) {
        AudioJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));
        if (!job.getUserId().equals(userId) || job.isDeleted()) {
            throw new BaseException(CommonErrorCode.COMMON_FORBIDDEN);
        }
        return toResponse(job);
    }

    @Transactional
    public void deleteJob(Long jobId, Long userId) {
        AudioJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));
        if (!job.getUserId().equals(userId)) {
            throw new BaseException(CommonErrorCode.COMMON_FORBIDDEN);
        }
        job.setDeleted(true);
        jobRepository.save(job);
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

    public org.springframework.data.domain.Page<AudioJobResponse> searchJobsAdmin(
            java.time.Instant startTime,
            java.time.Instant endTime,
            Long userId,
            String msisdn,
            String search,
            org.springframework.data.domain.Pageable pageable) {

        org.springframework.data.jpa.domain.Specification<AudioJob> spec = org.springframework.data.jpa.domain.Specification.where(
            (root, query, cb) -> cb.equal(root.get("deleted"), false)
        );

        if (startTime != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), startTime));
        }
        if (endTime != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), endTime));
        }
        if (userId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("userId"), userId));
        }
        if (msisdn != null && !msisdn.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("msisdn"), msisdn));
        }
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("prompt")), pattern),
                cb.like(root.get("msisdn"), pattern)
            ));
        }

        return jobRepository.findAll(spec, pageable).map(this::toResponse);
    }

    public AudioJobResponse getJobAdmin(Long jobId) {
        AudioJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));
        return toResponse(job);
    }

    @Transactional
    public AudioJobResponse createJobAdmin(Long explicitUserId, GenerateAudioRequest request) {
        AudioJob job = new AudioJob();
        job.setUserId(explicitUserId != null ? explicitUserId : 0L);
        job.setPrompt(request.prompt());
        job.setVoiceId(request.voiceId());
        job.setJobType(request.type() != null ? request.type() : "DIY");
        job.setAudioFileKey(request.audioFileKey());
        job.setVocalStart(request.vocalStart());
        job.setVocalEnd(request.vocalEnd());
        job.setStatus(AudioJob.JobStatus.COMPLETED);
        job.setResultUrl(request.audioFileKey() != null ? request.audioFileKey() : "http://localhost:9000/media-audio/mock.mp3");
        job.setTitle(request.title() != null ? request.title() : "Admin Custom Tone");
        job.setMsisdn(request.msisdn() != null ? request.msisdn() : "0000000000");

        jobRepository.save(job);
        return toResponse(job);
    }

    @Transactional
    public AudioJobResponse updateJobAdmin(Long jobId, AudioJobResponse request) {
        AudioJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));

        if (request.title() != null) {
            job.setTitle(request.title());
        }
        if (request.prompt() != null) {
            job.setPrompt(request.prompt());
        }
        if (request.status() != null) {
            job.setStatus(request.status());
        }
        if (request.resultUrl() != null) {
            job.setResultUrl(request.resultUrl());
        }

        jobRepository.save(job);
        return toResponse(job);
    }

    @Transactional
    public AudioJobResponse updateJob(Long jobId, AudioJobResponse request, Long userId) {
        AudioJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));
        boolean isAdmin = com.platform.common.security.SecurityUtils.getCurrentUserRoles().contains("ADMIN");
        if (!isAdmin && !job.getUserId().equals(userId)) {
            throw new BaseException(CommonErrorCode.COMMON_FORBIDDEN);
        }
        if (request.title() != null) {
            job.setTitle(request.title());
        }
        jobRepository.save(job);
        return toResponse(job);
    }

    @Transactional
    public void deleteJobAdmin(Long jobId, boolean hard) {
        AudioJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));
        if (hard) {
            jobRepository.delete(job);
        } else {
            job.setDeleted(true);
            jobRepository.save(job);
        }
    }

    private AudioJobResponse toResponse(AudioJob job) {
        return new AudioJobResponse(job.getId(), job.getPrompt(), job.getVoiceId(),
            job.getStatus(), job.getResultUrl(), job.getErrorMessage(), job.getCreatedAt(),
            job.getTitle(), job.getMsisdn());
    }
}
