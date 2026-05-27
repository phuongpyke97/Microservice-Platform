package com.platform.fileservice.service;

import com.platform.common.core.exception.BaseException;
import com.platform.fileservice.config.MinioProperties;
import com.platform.fileservice.dto.response.FileMetadataResponse;
import com.platform.fileservice.dto.response.PresignedUrlResponse;
import com.platform.fileservice.entity.FileMetadata;
import com.platform.fileservice.entity.FileStatus;
import com.platform.fileservice.exception.FileErrorCode;
import com.platform.fileservice.repository.FileMetadataRepository;
import io.minio.CopyObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class FileService {

    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final long PRESIGNED_TTL_SECONDS = 300;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "audio/mpeg", "audio/wav", "audio/ogg"
    );

    private final FileMetadataRepository repository;
    private final MinioClient minioClient;
    private final MinioClient publicMinioClient;
    private final MinioProperties properties;

    public FileService(FileMetadataRepository repository,
                       MinioClient minioClient,
                       @org.springframework.beans.factory.annotation.Qualifier("publicMinioClient") MinioClient publicMinioClient,
                       MinioProperties properties) {
        this.repository = repository;
        this.minioClient = minioClient;
        this.publicMinioClient = publicMinioClient;
        this.properties = properties;
    }

    @Transactional
    public FileMetadataResponse uploadTemp(Long userId, MultipartFile file) {
        validate(file.getSize(), file.getContentType());
        String objectKey = buildObjectKey(file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    io.minio.PutObjectArgs.builder()
                            .bucket(properties.bucketTemp())
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
        } catch (Exception e) {
            throw new BaseException(FileErrorCode.FILE_UPLOAD_FAILED);
        }

        FileMetadata metadata = repository.save(new FileMetadata(
                userId,
                file.getOriginalFilename(),
                objectKey,
                properties.bucketTemp(),
                file.getContentType(),
                file.getSize(),
                FileStatus.UPLOADED
        ));
        return toResponse(metadata);
    }

    @Transactional
    public FileMetadataResponse confirm(Long fileId, String targetBucket) {
        FileMetadata metadata = repository.findById(fileId)
                .orElseThrow(() -> new BaseException(FileErrorCode.FILE_NOT_FOUND));
        if (metadata.getStatus() == FileStatus.DELETED) {
            throw new BaseException(FileErrorCode.FILE_ALREADY_DELETED);
        }
        if (metadata.getStatus() != FileStatus.UPLOADED || !properties.bucketTemp().equals(metadata.getBucket())) {
            throw new BaseException(FileErrorCode.FILE_NOT_IN_TEMP);
        }
        if (!targetBucket.equals(properties.bucketAudio()) 
                && !targetBucket.equals(properties.bucketImage())
                && !targetBucket.equals(properties.bucketAudioLib())) {
            throw new BaseException(FileErrorCode.FILE_INVALID_TARGET_BUCKET);
        }

        try {
            // Get actual object size from MinIO first
            var stat = minioClient.statObject(
                    io.minio.StatObjectArgs.builder()
                            .bucket(metadata.getBucket())
                            .object(metadata.getStoredKey())
                            .build()
            );
            metadata.setSizeBytes(stat.size());

            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(targetBucket)
                            .object(metadata.getStoredKey())
                            .source(io.minio.CopySource.builder()
                                    .bucket(metadata.getBucket())
                                    .object(metadata.getStoredKey())
                                    .build())
                            .build()
            );
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(metadata.getBucket())
                            .object(metadata.getStoredKey())
                            .build()
            );
        } catch (Exception e) {
            throw new BaseException(FileErrorCode.FILE_UPLOAD_FAILED);
        }

        metadata.setBucket(targetBucket);
        metadata.setStatus(FileStatus.CONFIRMED);
        return toResponse(repository.save(metadata));
    }

    @Transactional(readOnly = true)
    public PresignedUrlResponse getDownloadUrl(Long fileId) {
        FileMetadata metadata = repository.findById(fileId)
                .orElseThrow(() -> new BaseException(FileErrorCode.FILE_NOT_FOUND));
        return new PresignedUrlResponse(metadata.getId(), metadata.getStoredKey(), presign(metadata.getBucket(), metadata.getStoredKey(), Method.GET), PRESIGNED_TTL_SECONDS);
    }

    @Transactional
    public PresignedUrlResponse getUploadUrl(Long userId, String originalName, String contentType) {
        validate(1, contentType);
        String objectKey = buildObjectKey(originalName);
        FileMetadata metadata = repository.save(new FileMetadata(
                userId,
                originalName,
                objectKey,
                properties.bucketTemp(),
                contentType,
                0L,
                FileStatus.UPLOADED
        ));
        return new PresignedUrlResponse(metadata.getId(), objectKey, presign(properties.bucketTemp(), objectKey, Method.PUT), PRESIGNED_TTL_SECONDS);
    }

    @Transactional
    public FileMetadataResponse softDelete(Long fileId) {
        FileMetadata metadata = repository.findById(fileId)
                .orElseThrow(() -> new BaseException(FileErrorCode.FILE_NOT_FOUND));
        if (metadata.getStatus() == FileStatus.DELETED) {
            throw new BaseException(FileErrorCode.FILE_ALREADY_DELETED);
        }
        metadata.setStatus(FileStatus.DELETED);
        return toResponse(repository.save(metadata));
    }

    private void validate(long size, String contentType) {
        if (size > MAX_SIZE) {
            throw new BaseException(FileErrorCode.FILE_TOO_LARGE);
        }
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BaseException(FileErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
    }

    private String presign(String bucket, String objectKey, Method method) {
        try {
            return publicMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .method(method)
                            .expiry((int) PRESIGNED_TTL_SECONDS, TimeUnit.SECONDS)
                            .build()
            );
        } catch (Exception e) {
            throw new BaseException(FileErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    public String uploadAudioBytes(byte[] bytes, String bucket) {
        String objectName = "lyria-" + UUID.randomUUID() + ".mp3";
        try (java.io.InputStream is = new java.io.ByteArrayInputStream(bytes)) {
            minioClient.putObject(
                io.minio.PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(is, bytes.length, -1)
                    .contentType("audio/mpeg")
                    .build()
            );
        } catch (Exception e) {
            throw new BaseException(FileErrorCode.FILE_UPLOAD_FAILED);
        }
        return properties.publicEndpoint() + "/" + bucket + "/" + objectName;
    }

    private String buildObjectKey(String originalName) {
        return UUID.randomUUID() + "-" + originalName;
    }

    private FileMetadataResponse toResponse(FileMetadata metadata) {
        return new FileMetadataResponse(
                metadata.getId(),
                metadata.getUserId(),
                metadata.getOriginalName(),
                metadata.getStoredKey(),
                metadata.getBucket(),
                metadata.getContentType(),
                metadata.getSizeBytes(),
                metadata.getStatus()
        );
    }
}
