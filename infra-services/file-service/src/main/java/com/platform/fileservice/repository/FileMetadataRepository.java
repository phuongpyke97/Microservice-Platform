package com.platform.fileservice.repository;

import com.platform.fileservice.entity.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {
    Optional<FileMetadata> findByStoredKey(String storedKey);
    java.util.List<FileMetadata> findByIdGreaterThanAndStatusAndCreatedAtBeforeOrderByIdAsc(
            Long lastId, 
            com.platform.fileservice.entity.FileStatus status, 
            java.time.Instant cutoffTime, 
            org.springframework.data.domain.Pageable pageable
    );
    java.util.List<FileMetadata> findByBucketAndStatusAndContentTypeStartingWith(
            String bucket, 
            com.platform.fileservice.entity.FileStatus status, 
            String contentTypePrefix
    );
}
