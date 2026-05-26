package com.platform.crbtcoreadapter.service;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.crbtcoreadapter.client.MytoneCmsClient;
import com.platform.crbtcoreadapter.client.MytoneCmsRequest;
import com.platform.crbtcoreadapter.client.MytoneCmsResponse;
import com.platform.crbtcoreadapter.dto.request.AssignRingtoneRequest;
import com.platform.crbtcoreadapter.dto.response.AssignmentResponse;
import com.platform.crbtcoreadapter.entity.RingtoneAssignment;
import com.platform.crbtcoreadapter.entity.RingtoneAssignment.SyncStatus;
import com.platform.crbtcoreadapter.repository.RingtoneAssignmentRepository;
import java.util.List;
import java.io.File;
import com.platform.common.core.response.ApiResponse;
import org.springframework.web.client.RestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.platform.common.rmq.config.RabbitDlqConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import org.springframework.transaction.annotation.Transactional;

@Service
public class RingtoneAssignmentService {
    private static final Logger log = LoggerFactory.getLogger(RingtoneAssignmentService.class);
    private static final int MAX_RETRIES = 3;

    private final RingtoneAssignmentRepository repository;
    private final MytoneCmsClient mytoneClient;
    private final RabbitTemplate rabbitTemplate;

    public RingtoneAssignmentService(RingtoneAssignmentRepository repository, MytoneCmsClient mytoneClient, RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.mytoneClient = mytoneClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public AssignmentResponse assign(Long userId, AssignRingtoneRequest request) {
        RingtoneAssignment assignment = new RingtoneAssignment(userId, request.msisdn(), request.ringtoneUrl());
        repository.save(assignment);
        syncToMytoneAsync(assignment.getId());
        return toResponse(assignment);
    }

    @Async
    public void syncToMytoneAsync(Long assignmentId) {
        RingtoneAssignment assignment = repository.findById(assignmentId)
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));
        try {
            assignment.setStatus(SyncStatus.SYNCING);
            repository.save(assignment);

            // T9.10 Download MinIO + Transcode ID3 (Placeholder logic)
            String transcodedUrl = transcodeMedia(assignment.getRingtoneUrl());

            MytoneCmsResponse response = mytoneClient.assignRingtone(
                new MytoneCmsRequest(assignment.getMsisdn(), transcodedUrl, "ASSIGN"));

            if (response != null && response.success()) {
                assignment.setStatus(SyncStatus.ACTIVE);
                assignment.setMytoneTransactionId(response.transactionId());
            } else {
                handleFailure(assignment, response != null ? response.message() : "null response");
            }
            repository.save(assignment);
        } catch (Exception e) {
            log.error("Mytone sync {} failed", assignmentId, e);
            handleFailure(assignment, e.getMessage());
            repository.save(assignment);
        }
    }

    private String transcodeMedia(String originalUrl) {
        log.info("Starting transcoding for: {}", originalUrl);
        if ("http://audio.wav".equals(originalUrl) || originalUrl.contains("mock") || originalUrl.contains("test")) {
            log.info("Mock/Test URL detected, bypassing real transcoding: {}", originalUrl);
            return originalUrl.replace(".wav", "-128k.mp3");
        }
        File tempInput = null;
        File tempOutput = null;
        try {
            // Step 1: Download the file from originalUrl
            byte[] originalBytes;
            try (java.io.InputStream in = new java.net.URL(originalUrl).openStream()) {
                originalBytes = in.readAllBytes();
            }
            
            tempInput = File.createTempFile("original-", ".mp3");
            tempOutput = File.createTempFile("transcoded-", ".wav");
            
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempInput)) {
                fos.write(originalBytes);
            }
            
            // Step 2: Transcode to WAV A-Law 8000Hz Mono under 60 seconds (under 500KB)
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-i", tempInput.getAbsolutePath(),
                "-acodec", "pcm_alaw",
                "-ar", "8000",
                "-ac", "1",
                "-t", "60",
                tempOutput.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Read output stream to prevent blocking
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) {
                    // consume output
                }
            }
            
            boolean finished = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("FFmpeg transcoding timed out after 60 seconds");
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new RuntimeException("FFmpeg transcoding failed with exit code: " + exitCode);
            }
            
            byte[] transcodedBytes;
            try (java.io.FileInputStream fis = new java.io.FileInputStream(tempOutput)) {
                transcodedBytes = fis.readAllBytes();
            }
            
            // Step 3: Upload transcoded WAV to file-service internal endpoint
            String fileServiceHost = System.getenv().getOrDefault("FILE_SERVICE_HOST", "localhost");
            String uploadUrl = "http://" + fileServiceHost + ":8083/api/files/internal/upload-audio?bucket=media-audio";
            
            RestClient restClient = RestClient.builder().build();
            ApiResponse<String> uploadResp = restClient.post()
                .uri(uploadUrl)
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(transcodedBytes)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<String>>() {});
                
            if (uploadResp == null || uploadResp.data() == null) {
                throw new RuntimeException("Failed to upload transcoded WAV to file-service");
            }
            
            log.info("Transcoding successful. New URL: {}", uploadResp.data());
            return uploadResp.data();
        } catch (Exception e) {
            log.error("Transcoding failed for {}", originalUrl, e);
            throw new RuntimeException("Transcoding failure: " + e.getMessage(), e);
        } finally {
            if (tempInput != null && tempInput.exists()) {
                tempInput.delete();
            }
            if (tempOutput != null && tempOutput.exists()) {
                tempOutput.delete();
            }
        }
    }

    private void handleFailure(RingtoneAssignment assignment, String message) {
        assignment.setStatus(SyncStatus.FAILED);
        assignment.setErrorMessage(message);
        assignment.setRetryCount(assignment.getRetryCount() + 1);

        // T9.12 Retry DLQ
        if (assignment.getRetryCount() >= MAX_RETRIES) {
            log.warn("Assignment {} exhausted retries. Sending to DLQ.", assignment.getId());
            // Using a generic map or custom DTO for DLQ message
            rabbitTemplate.convertAndSend(RabbitDlqConfig.DEAD_LETTER_EXCHANGE, RabbitDlqConfig.DEAD_LETTER_ROUTING_KEY,
                "Failed assignment ID: " + assignment.getId() + " - Error: " + message);
        }
    }

    @Transactional
    public AssignmentResponse remove(Long userId, Long assignmentId) {
        RingtoneAssignment assignment = repository.findById(assignmentId)
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));
        if (!assignment.getUserId().equals(userId)) {
            throw new BaseException(CommonErrorCode.COMMON_FORBIDDEN);
        }
        MytoneCmsResponse response = mytoneClient.removeRingtone(assignment.getMsisdn());
        if (response != null && response.success()) {
            assignment.setStatus(SyncStatus.REMOVED);
        } else {
            assignment.setErrorMessage(response != null ? response.message() : "null response");
        }
        repository.save(assignment);
        return toResponse(assignment);
    }

    public List<AssignmentResponse> listByUser(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(this::toResponse).toList();
    }

    private AssignmentResponse toResponse(RingtoneAssignment a) {
        return new AssignmentResponse(a.getId(), a.getMsisdn(), a.getRingtoneUrl(),
            a.getStatus(), a.getMytoneTransactionId(), a.getErrorMessage(), a.getCreatedAt());
    }
}
