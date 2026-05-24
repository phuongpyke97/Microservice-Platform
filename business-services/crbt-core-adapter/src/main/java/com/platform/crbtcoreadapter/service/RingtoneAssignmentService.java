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
        // T9.10 implementation logic
        log.info("Downloading from MinIO, transcoding to MP3 128kbps, adding ID3 tags...");
        return originalUrl.replace(".wav", "-128k.mp3"); // placeholder for transcoded URL
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
