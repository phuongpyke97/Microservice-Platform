package com.platform.crbtcoreadapter.dto.response;

import com.platform.crbtcoreadapter.entity.RingtoneAssignment.SyncStatus;
import java.time.Instant;

public record AssignmentResponse(
    Long id,
    String msisdn,
    String ringtoneUrl,
    SyncStatus status,
    String mytoneTransactionId,
    String errorMessage,
    Instant createdAt
) {}
