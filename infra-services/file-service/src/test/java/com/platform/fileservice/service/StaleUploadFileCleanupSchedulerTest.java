package com.platform.fileservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.platform.fileservice.config.MinioProperties;
import com.platform.fileservice.entity.FileMetadata;
import com.platform.fileservice.entity.FileStatus;
import com.platform.fileservice.repository.FileMetadataRepository;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class StaleUploadFileCleanupSchedulerTest {

    @Mock FileMetadataRepository repository;
    @Mock MinioClient minioClient;
    @Mock MinioProperties properties;

    @InjectMocks StaleUploadFileCleanupScheduler scheduler;

    @Test
    void cleanupStaleUploads_shouldPurgeStaleFiles() throws Exception {
        FileMetadata staleFile = new FileMetadata(1L, "a.png", "obj-1", "temp", "image/png", 100, FileStatus.UPLOADED);
        setId(staleFile, 101L);

        when(repository.findByIdGreaterThanAndStatusAndCreatedAtBeforeOrderByIdAsc(
                eq(0L), eq(FileStatus.UPLOADED), any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of(staleFile));

        scheduler.cleanupStaleUploads();

        // 1. Verify MinIO removeObject was called
        ArgumentCaptor<RemoveObjectArgs> minioCaptor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(minioCaptor.capture());
        assertEquals("temp", minioCaptor.getValue().bucket());
        assertEquals("obj-1", minioCaptor.getValue().object());

        // 2. Verify status updated to DELETED and saved to repository
        assertEquals(FileStatus.DELETED, staleFile.getStatus());
        verify(repository).save(staleFile);
    }

    @Test
    void cleanupStaleUploads_shouldContinueProcessing_whenMinioThrowsException() throws Exception {
        FileMetadata staleFile1 = new FileMetadata(1L, "a.png", "obj-1", "temp", "image/png", 100, FileStatus.UPLOADED);
        setId(staleFile1, 101L);
        FileMetadata staleFile2 = new FileMetadata(1L, "b.png", "obj-2", "temp", "image/png", 100, FileStatus.UPLOADED);
        setId(staleFile2, 102L);

        when(repository.findByIdGreaterThanAndStatusAndCreatedAtBeforeOrderByIdAsc(
                eq(0L), eq(FileStatus.UPLOADED), any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of(staleFile1, staleFile2));

        // Throws exception for the first file, but should succeed for the second
        doThrow(new RuntimeException("MinIO connection failed")).when(minioClient).removeObject(any(RemoveObjectArgs.class));

        scheduler.cleanupStaleUploads();

        // Both removeObject attempts should have been called
        verify(minioClient, times(2)).removeObject(any(RemoveObjectArgs.class));
        
        // Even if MinIO fails, we catch the exception and proceed. Since the exception is thrown,
        // the status update and DB save might be skipped for that failed record in the try block
        verify(repository, never()).save(staleFile1);
        verify(repository, never()).save(staleFile2);
    }

    private void setId(FileMetadata metadata, Long id) {
        try {
            var field = FileMetadata.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(metadata, id);
        } catch (Exception ignored) {
        }
    }
}
