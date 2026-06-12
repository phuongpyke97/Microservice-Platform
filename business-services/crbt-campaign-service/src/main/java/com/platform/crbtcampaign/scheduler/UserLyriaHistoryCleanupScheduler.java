package com.platform.crbtcampaign.scheduler;

import com.platform.common.core.response.ApiResponse;
import com.platform.crbtcampaign.client.CrbtCoreAdapterClient;
import com.platform.crbtcampaign.client.FileServiceClient;
import com.platform.crbtcampaign.entity.UserLyriaHistory;
import com.platform.crbtcampaign.repository.UserLyriaHistoryRepository;
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
public class UserLyriaHistoryCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(UserLyriaHistoryCleanupScheduler.class);
    private static final int BATCH_SIZE = 100;

    private final UserLyriaHistoryRepository historyRepository;
    private final CrbtCoreAdapterClient crbtCoreAdapterClient;
    private final FileServiceClient fileServiceClient;

    public UserLyriaHistoryCleanupScheduler(UserLyriaHistoryRepository historyRepository,
                                             CrbtCoreAdapterClient crbtCoreAdapterClient,
                                             FileServiceClient fileServiceClient) {
        this.historyRepository = historyRepository;
        this.crbtCoreAdapterClient = crbtCoreAdapterClient;
        this.fileServiceClient = fileServiceClient;
    }

    // Scheduled to run every minute temporarily for UAT testing
    @Scheduled(cron = "0 */1 * * * *")
    public void cleanupStaleLyriaHistory() {
        log.info("[LYRIA-HISTORY-CLEANUP] Starting stale Lyria history cleanup job...");
        Instant cutoffTime = Instant.now().minus(7, ChronoUnit.DAYS);

        long lastId = 0L;
        long totalPurged = 0;

        while (true) {
            List<UserLyriaHistory> candidates = historyRepository.findByIdGreaterThanAndDeletedFalseAndCreatedAtBeforeOrderByIdAsc(
                    lastId, cutoffTime, PageRequest.of(0, BATCH_SIZE));

            if (candidates.isEmpty()) {
                break;
            }

            log.info("[LYRIA-HISTORY-CLEANUP] Processing batch of {} candidate history records", candidates.size());

            // Collect all candidate URLs
            List<String> urlsToCheck = new ArrayList<>();
            for (UserLyriaHistory item : candidates) {
                if (item.getAudioUrl() != null && !item.getAudioUrl().isBlank()) {
                    urlsToCheck.add(item.getAudioUrl().trim());
                }
            }

            // Call crbt-core-adapter to verify which URLs are active ringback tones
            Set<String> activeUrls = new HashSet<>();
            if (!urlsToCheck.isEmpty()) {
                try {
                    ApiResponse<List<String>> checkResp = crbtCoreAdapterClient.activeCheck(urlsToCheck);
                    if (checkResp != null && checkResp.success() && checkResp.data() != null) {
                        activeUrls.addAll(checkResp.data());
                    } else {
                        log.error("[LYRIA-HISTORY-CLEANUP] Failed to verify active assignments: {}", checkResp != null ? checkResp.message() : "null response");
                        break; // Stop current batch to prevent accidental deletions
                    }
                } catch (Exception e) {
                    log.error("[LYRIA-HISTORY-CLEANUP] Exception checking active assignments. Aborting current run.", e);
                    break;
                }
            }

            // Process each candidate in the batch
            for (UserLyriaHistory item : candidates) {
                lastId = item.getId();

                String url = item.getAudioUrl();
                if (url == null || url.isBlank()) {
                    item.setDeleted(true);
                    historyRepository.save(item);
                    continue;
                }

                if (activeUrls.contains(url.trim())) {
                    log.info("[LYRIA-HISTORY-CLEANUP] History ID {} is active. Skipping deletion.", item.getId());
                } else {
                    try {
                        // Delete from physical storage
                        ApiResponse<Void> deleteResp = fileServiceClient.deleteFileByUrl(url.trim());
                        if (deleteResp != null && !deleteResp.success()) {
                            String msg = deleteResp.message() != null ? deleteResp.message() : "";
                            // Skip soft-deletion in DB only if it's a real failure, but accept 404 FILE_NOT_FOUND as deleted
                            if (!msg.contains("FILE_NOT_FOUND")) {
                                log.warn("[LYRIA-HISTORY-CLEANUP] File service returned error for {}: {}", url, deleteResp.message());
                                continue;
                            }
                        }

                        // Mark as deleted in campaign DB
                        item.setDeleted(true);
                        historyRepository.save(item);
                        totalPurged++;
                    } catch (Exception e) {
                        log.error("[LYRIA-HISTORY-CLEANUP] Error deleting URL: {}", url, e);
                    }
                }
            }

            if (candidates.size() < BATCH_SIZE) {
                break;
            }
        }

        log.info("[LYRIA-HISTORY-CLEANUP] Stale Lyria history cleanup job finished. Purged {} history records.", totalPurged);
    }
}
