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
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

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
    private final RestClient restClient;

    public FileService(FileMetadataRepository repository,
                       MinioClient minioClient,
                       @org.springframework.beans.factory.annotation.Qualifier("publicMinioClient") MinioClient publicMinioClient,
                       MinioProperties properties,
                       @Value("${ai-worker.url:http://localhost:8765}") String aiWorkerUrl) {
        this.repository = repository;
        this.minioClient = minioClient;
        this.publicMinioClient = publicMinioClient;
        this.properties = properties;
        this.restClient = RestClient.builder().baseUrl(aiWorkerUrl).build();
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

        // Get actual object size from MinIO first to validate size before processing
        long actualSize;
        try {
            var stat = minioClient.statObject(
                    io.minio.StatObjectArgs.builder()
                            .bucket(metadata.getBucket())
                            .object(metadata.getStoredKey())
                            .build()
            );
            actualSize = stat.size();
        } catch (Exception e) {
            throw new BaseException(FileErrorCode.FILE_NOT_FOUND, "Failed to locate file in temp storage");
        }

        // Enforce the size validation limit (e.g. 5MB)
        validate(actualSize, metadata.getContentType());
        metadata.setSizeBytes(actualSize);

        // Enforce audio-specific constraints (duration and vocal presence)
        if (metadata.getContentType() != null && metadata.getContentType().startsWith("audio/")) {
            try {
                byte[] fileBytes;
                try (InputStream is = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(metadata.getBucket())
                                .object(metadata.getStoredKey())
                                .build())) {
                    fileBytes = is.readAllBytes();
                }

                // Verify audio duration (40s -> 5 minutes)
                double duration = getAudioDuration(fileBytes, metadata.getOriginalName(), metadata.getContentType());
                if (duration < 40.0 || duration > 300.0) {
                    throw new BaseException(FileErrorCode.INVALID_AUDIO_DURATION);
                }

                // Vocal check if target bucket is media-audio-lib
                if (targetBucket.equals(properties.bucketAudioLib())) {
                    checkVocal(fileBytes, metadata.getOriginalName(), metadata.getContentType());
                }
            } catch (BaseException e) {
                throw e;
            } catch (Exception e) {
                throw new BaseException(FileErrorCode.FILE_UPLOAD_FAILED, "Failed to validate audio file: " + e.getMessage());
            }
        }

        try {
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
        } catch (BaseException e) {
            throw e;
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

    @Transactional(readOnly = true)
    public PresignedUrlResponse getInternalDownloadUrl(Long fileId) {
        FileMetadata metadata = repository.findById(fileId)
                .orElseThrow(() -> new BaseException(FileErrorCode.FILE_NOT_FOUND));
        return new PresignedUrlResponse(metadata.getId(), metadata.getStoredKey(), presignInternal(metadata.getBucket(), metadata.getStoredKey(), Method.GET), PRESIGNED_TTL_SECONDS);
    }

    @Transactional(readOnly = true)
    public byte[] downloadFile(Long fileId) {
        FileMetadata metadata = repository.findById(fileId)
                .orElseThrow(() -> new BaseException(FileErrorCode.FILE_NOT_FOUND));
        try (InputStream is = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(metadata.getBucket())
                        .object(metadata.getStoredKey())
                        .build())) {
            return is.readAllBytes();
        } catch (Exception e) {
            throw new BaseException(FileErrorCode.FILE_NOT_FOUND);
        }
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

    private String presignInternal(String bucket, String objectKey, Method method) {
        try {
            return minioClient.getPresignedObjectUrl(
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

    private void checkVocal(byte[] fileBytes, String filename, String contentType) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            String mimeType = contentType != null ? contentType : "audio/mpeg";
            builder.part("file", new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            }, MediaType.parseMediaType(mimeType));

            MultiValueMap<String, HttpEntity<?>> parts = builder.build();

            Map<String, Object> response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/separate-audio")
                            .queryParam("exclude_audio", "true")
                            .build())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

            if (response != null) {
                Object hasVocalObj = response.get("has_vocal");
                boolean hasVocal = false;
                if (hasVocalObj instanceof Boolean) {
                    hasVocal = (Boolean) hasVocalObj;
                } else if (hasVocalObj instanceof String) {
                    hasVocal = Boolean.parseBoolean((String) hasVocalObj);
                }

                if (hasVocal) {
                    throw new BaseException(FileErrorCode.AUDIO_HAS_VOCAL);
                }
            }
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw new BaseException(FileErrorCode.FILE_UPLOAD_FAILED, "Failed to analyze vocal presence: " + e.getMessage());
        }
    }

    private double getAudioDuration(byte[] fileBytes, String filename, String contentType) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            String mimeType = contentType != null ? contentType : "audio/mpeg";
            builder.part("file", new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            }, MediaType.parseMediaType(mimeType));

            MultiValueMap<String, HttpEntity<?>> parts = builder.build();

            Map<String, Object> response = restClient.post()
                    .uri("/audio-metadata")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

            if (response != null && response.get("duration") != null) {
                return ((Number) response.get("duration")).doubleValue();
            }
            throw new BaseException(FileErrorCode.FILE_UPLOAD_FAILED, "Failed to parse audio duration");
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw new BaseException(FileErrorCode.FILE_UPLOAD_FAILED, "Failed to analyze audio duration: " + e.getMessage());
        }
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
