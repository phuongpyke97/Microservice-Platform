package com.platform.fileservice.service;

import com.platform.fileservice.config.MinioProperties;
import com.platform.fileservice.entity.FileMetadata;
import com.platform.fileservice.entity.FileStatus;
import com.platform.fileservice.repository.FileMetadataRepository;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class StaleUploadFileCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(StaleUploadFileCleanupScheduler.class);
    private static final int BATCH_SIZE = 100;

    private final FileMetadataRepository repository;
    private final MinioClient minioClient;
    private final MinioProperties properties;

    public StaleUploadFileCleanupScheduler(FileMetadataRepository repository,
                                           MinioClient minioClient,
                                           MinioProperties properties) {
        this.repository = repository;
        this.minioClient = minioClient;
        this.properties = properties;
    }

    // Temporarily scheduled to run every minute for testing in local/UAT
    @Scheduled(cron = "0 */1 * * * *")
    public void cleanupStaleUploads() {
        log.info("[STALE-CLEANUP] Starting stale file upload cleanup job...");
        Instant cutoffTime = Instant.now().minus(24, ChronoUnit.HOURS);

        long lastId = 0L;
        long totalPurged = 0;

        while (true) {
            List<FileMetadata> candidates = repository.findByIdGreaterThanAndStatusAndCreatedAtBeforeOrderByIdAsc(
                    lastId, FileStatus.UPLOADED, cutoffTime, PageRequest.of(0, BATCH_SIZE));

            if (candidates.isEmpty()) {
                break;
            }

            log.info("[STALE-CLEANUP] Found {} stale unconfirmed upload candidates in database", candidates.size());

            for (FileMetadata metadata : candidates) {
                lastId = metadata.getId();

                try {
                    // 1. Remove from MinIO temp bucket
                    String bucketName = metadata.getBucket() != null ? metadata.getBucket() : properties.bucketTemp();
                    String objectKey = metadata.getStoredKey();

                    if (objectKey != null && !objectKey.isBlank()) {
                        minioClient.removeObject(
                                RemoveObjectArgs.builder()
                                        .bucket(bucketName)
                                        .object(objectKey)
                                        .build()
                        );
                    }

                    // 2. Soft-delete by setting status = DELETED
                    metadata.setStatus(FileStatus.DELETED);
                    repository.save(metadata);
                    totalPurged++;
                } catch (Exception e) {
                    log.error("[STALE-CLEANUP] Error purging stale file ID {}: {}", metadata.getId(), e.getMessage());
                }
            }

            if (candidates.size() < BATCH_SIZE) {
                break;
            }
        }

        log.info("[STALE-CLEANUP] Stale file cleanup finished. Purged {} file metadata records.", totalPurged);
    }
}
