package com.platform.audiogeneration.scheduler;

import com.platform.audiogeneration.client.CrbtCoreAdapterClient;
import com.platform.audiogeneration.client.FileServiceClient;
import com.platform.audiogeneration.entity.AudioJob;
import com.platform.audiogeneration.repository.AudioJobRepository;
import com.platform.common.core.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class UnusedAudioCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(UnusedAudioCleanupScheduler.class);
    private static final int BATCH_SIZE = 100;

    private final AudioJobRepository jobRepository;
    private final CrbtCoreAdapterClient crbtCoreAdapterClient;
    private final FileServiceClient fileServiceClient;

    public UnusedAudioCleanupScheduler(AudioJobRepository jobRepository,
                                       CrbtCoreAdapterClient crbtCoreAdapterClient,
                                       FileServiceClient fileServiceClient) {
        this.jobRepository = jobRepository;
        this.crbtCoreAdapterClient = crbtCoreAdapterClient;
        this.fileServiceClient = fileServiceClient;
    }

    @Scheduled(cron = "0 */1 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void cleanupUnusedAudios() {
        log.info("[CLEANUP-SCHEDULER] Starting daily unused audio cleanup task...");
        Instant cutoffTime = Instant.now().minus(7, ChronoUnit.DAYS);

        long lastId = 0L;
        long totalPurged = 0;

        while (true) {
            List<AudioJob> candidates = jobRepository.findByIdGreaterThanAndCreatedAtBeforeAndDeletedFalseOrderByIdAsc(
                    lastId, cutoffTime, PageRequest.of(0, BATCH_SIZE));

            if (candidates.isEmpty()) {
                break;
            }

            log.info("[CLEANUP-SCHEDULER] Processing batch of {} candidate jobs", candidates.size());

            // Collect all URLs to check
            List<String> urlsToCheck = new ArrayList<>();
            for (AudioJob job : candidates) {
                if (job.getResultUrl() != null && !job.getResultUrl().isBlank()) {
                    String[] parts = job.getResultUrl().split(",");
                    for (String part : parts) {
                        if (!part.trim().isEmpty()) {
                            urlsToCheck.add(part.trim());
                        }
                    }
                }
            }

            // Call crbt-core-adapter to find which of these URLs are actively set as ringtones
            Set<String> activeUrls = new HashSet<>();
            if (!urlsToCheck.isEmpty()) {
                try {
                    ApiResponse<List<String>> checkResp = crbtCoreAdapterClient.activeCheck(urlsToCheck);
                    if (checkResp != null && checkResp.success() && checkResp.data() != null) {
                        activeUrls.addAll(checkResp.data());
                    } else {
                        log.error("[CLEANUP-SCHEDULER] Failed to check active assignments: {}", checkResp != null ? checkResp.message() : "null response");
                        // In case of communication error, abort this run to prevent accidental deletions
                        break;
                    }
                } catch (Exception e) {
                    log.error("[CLEANUP-SCHEDULER] Exception checking active assignments. Aborting current run.", e);
                    break;
                }
            }

            // Evaluate each candidate and delete if unused
            for (AudioJob job : candidates) {
                lastId = job.getId(); // Update cursor to the current job ID

                if (job.getResultUrl() == null || job.getResultUrl().isBlank()) {
                    // Empty URL is marked as deleted
                    job.setDeleted(true);
                    jobRepository.save(job);
                    continue;
                }

                String[] parts = job.getResultUrl().split(",");
                boolean isInUse = false;
                for (String part : parts) {
                    if (activeUrls.contains(part.trim())) {
                        isInUse = true;
                        break;
                    }
                }

                if (isInUse) {
                    log.info("[CLEANUP-SCHEDULER] Job ID {} is in use (active CRBT). Skipping deletion.", job.getId());
                } else {
                    boolean allDeleted = true;
                    for (String part : parts) {
                        String url = part.trim();
                        if (!url.isEmpty()) {
                            try {
                                ApiResponse<Void> deleteResp = fileServiceClient.deleteFileByUrl(url);
                                if (deleteResp != null && !deleteResp.success()) {
                                    String msg = deleteResp.message() != null ? deleteResp.message() : "";
                                    // Treat 404/FILE_NOT_FOUND as successfully gone, otherwise it's a failure
                                    if (!msg.contains("FILE_NOT_FOUND")) {
                                        log.warn("[CLEANUP-SCHEDULER] File service returned error for {}: {}", url, deleteResp.message());
                                        allDeleted = false;
                                    }
                                }
                            } catch (Exception e) {
                                log.error("[CLEANUP-SCHEDULER] Error deleting URL: {}", url, e);
                                allDeleted = false;
                            }
                        }
                    }

                    if (allDeleted) {
                        job.setDeleted(true);
                        jobRepository.save(job);
                        totalPurged++;
                    }
                }
            }

            // Safety break: if the batch size wasn't fully filled, we reached the end of the query candidates
            if (candidates.size() < BATCH_SIZE) {
                break;
            }
        }

        log.info("[CLEANUP-SCHEDULER] Daily unused audio cleanup task finished. Purged {} jobs.", totalPurged);
    }
}
