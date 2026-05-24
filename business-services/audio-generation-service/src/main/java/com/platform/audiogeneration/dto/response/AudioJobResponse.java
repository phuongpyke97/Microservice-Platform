package com.platform.audiogeneration.dto.response;

import com.platform.audiogeneration.entity.AudioJob.JobStatus;
import java.time.Instant;

public record AudioJobResponse(
    Long id,
    String prompt,
    String voiceId,
    JobStatus status,
    String resultUrl,
    String errorMessage,
    Instant createdAt
) {}
