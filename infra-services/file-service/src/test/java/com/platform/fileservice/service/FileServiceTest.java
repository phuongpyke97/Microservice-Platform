package com.platform.fileservice.service;

import com.platform.common.core.exception.BaseException;
import com.platform.fileservice.config.MinioProperties;
import com.platform.fileservice.dto.response.FileMetadataResponse;
import com.platform.fileservice.dto.response.PresignedUrlResponse;
import com.platform.fileservice.entity.FileMetadata;
import com.platform.fileservice.entity.FileStatus;
import com.platform.fileservice.exception.FileErrorCode;
import com.platform.fileservice.repository.FileMetadataRepository;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock private FileMetadataRepository repository;
    @Mock private MinioClient minioClient;
    @Mock private MinioClient publicMinioClient;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileService(
                repository,
                minioClient,
                publicMinioClient,
                new MinioProperties("http://localhost:9000", "http://localhost:9000", "minio", "minio123", "temp", "audio", "image", "audio-lib"),
                "http://localhost:8765",
                40000,
                300000
        );
    }

    @Test
    void uploadTemp_validFile_savesMetadata() throws Exception {
        var file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3});
        when(repository.save(any(FileMetadata.class))).thenAnswer(invocation -> {
            FileMetadata metadata = invocation.getArgument(0);
            setId(metadata, 1L);
            return metadata;
        });

        FileMetadataResponse response = fileService.uploadTemp(10L, file);

        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.bucket()).isEqualTo("temp");
        assertThat(response.status()).isEqualTo(FileStatus.UPLOADED);
        verify(repository).save(any(FileMetadata.class));
    }

    @Test
    void uploadTemp_largeFile_throws() {
        byte[] data = new byte[5 * 1024 * 1024 + 1];
        var file = new MockMultipartFile("file", "big.mp3", "audio/mpeg", data);

        assertThatThrownBy(() -> fileService.uploadTemp(10L, file))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getErrorCode()).isEqualTo(FileErrorCode.FILE_TOO_LARGE));
    }

    @Test
    void uploadTemp_invalidType_throws() {
        var file = new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1});

        assertThatThrownBy(() -> fileService.uploadTemp(10L, file))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getErrorCode()).isEqualTo(FileErrorCode.FILE_TYPE_NOT_ALLOWED));
    }

    @Test
    void getUploadUrl_returnsPresignedUrlWith300SecondTtl() throws Exception {
        when(publicMinioClient.getPresignedObjectUrl(any())).thenReturn("http://minio/upload");
        when(repository.save(any(FileMetadata.class))).thenAnswer(invocation -> {
            FileMetadata metadata = invocation.getArgument(0);
            setId(metadata, 5L);
            return metadata;
        });

        PresignedUrlResponse response = fileService.getUploadUrl(10L, "a.png", "image/png");

        assertThat(response.fileId()).isEqualTo(5L);
        assertThat(response.url()).isEqualTo("http://minio/upload");
        assertThat(response.expiresInSeconds()).isEqualTo(300L);
    }

    @Test
    void confirm_uploadedTempFile_movesToTargetBucket() throws Exception {
        FileMetadata metadata = new FileMetadata(1L, "a.png", "obj-1", "temp", "image/png", 10, FileStatus.UPLOADED);
        setId(metadata, 3L);
        when(repository.findById(3L)).thenReturn(Optional.of(metadata));
        when(repository.save(any(FileMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        io.minio.StatObjectResponse statResponse = mock(io.minio.StatObjectResponse.class);
        when(statResponse.size()).thenReturn(10L);
        when(minioClient.statObject(any())).thenReturn(statResponse);

        FileMetadataResponse response = fileService.confirm(3L, "image");

        assertThat(response.bucket()).isEqualTo("image");
        assertThat(response.status()).isEqualTo(FileStatus.CONFIRMED);
        verify(repository).save(any(FileMetadata.class));
    }

    @Test
    void softDelete_marksDeleted() {
        FileMetadata metadata = new FileMetadata(1L, "a.png", "obj-1", "image", "image/png", 10, FileStatus.CONFIRMED);
        setId(metadata, 4L);
        when(repository.findById(4L)).thenReturn(Optional.of(metadata));
        when(repository.save(any(FileMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FileMetadataResponse response = fileService.softDelete(4L);

        assertThat(response.status()).isEqualTo(FileStatus.DELETED);
    }

    @Test
    void confirm_audioFileWithValidDuration_succeeds() throws Exception {
        FileMetadata metadata = new FileMetadata(1L, "music.mp3", "obj-1", "temp", "audio/mpeg", 0, FileStatus.UPLOADED);
        setId(metadata, 10L);
        when(repository.findById(10L)).thenReturn(Optional.of(metadata));
        when(repository.save(any(FileMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));

        io.minio.StatObjectResponse statResponse = mock(io.minio.StatObjectResponse.class);
        when(statResponse.size()).thenReturn(1024L);
        when(minioClient.statObject(any())).thenReturn(statResponse);

        // Mock RestClient
        org.springframework.web.client.RestClient restClient = mock(org.springframework.web.client.RestClient.class);
        org.springframework.web.client.RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(org.springframework.web.client.RestClient.RequestBodyUriSpec.class);
        org.springframework.web.client.RestClient.RequestBodySpec requestBodySpec = mock(org.springframework.web.client.RestClient.RequestBodySpec.class);
        org.springframework.web.client.RestClient.ResponseSpec responseSpec = mock(org.springframework.web.client.RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);

        java.util.Map<String, Object> responseMap = java.util.Map.of("duration", 60.0);
        when(responseSpec.body(any(org.springframework.core.ParameterizedTypeReference.class))).thenReturn(responseMap);

        setRestClient(fileService, restClient);

        FileMetadataResponse response = fileService.confirm(10L, "audio");

        assertThat(response.bucket()).isEqualTo("audio");
        assertThat(response.status()).isEqualTo(FileStatus.CONFIRMED);
        assertThat(response.sizeBytes()).isEqualTo(1024L);
    }

    @Test
    void confirm_audioFileWithInvalidDuration_throws() throws Exception {
        FileMetadata metadata = new FileMetadata(1L, "music.mp3", "obj-1", "temp", "audio/mpeg", 0, FileStatus.UPLOADED);
        setId(metadata, 11L);
        when(repository.findById(11L)).thenReturn(Optional.of(metadata));

        io.minio.StatObjectResponse statResponse = mock(io.minio.StatObjectResponse.class);
        when(statResponse.size()).thenReturn(1024L);
        when(minioClient.statObject(any())).thenReturn(statResponse);

        // Mock RestClient
        org.springframework.web.client.RestClient restClient = mock(org.springframework.web.client.RestClient.class);
        org.springframework.web.client.RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(org.springframework.web.client.RestClient.RequestBodyUriSpec.class);
        org.springframework.web.client.RestClient.RequestBodySpec requestBodySpec = mock(org.springframework.web.client.RestClient.RequestBodySpec.class);
        org.springframework.web.client.RestClient.ResponseSpec responseSpec = mock(org.springframework.web.client.RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);

        java.util.Map<String, Object> responseMap = java.util.Map.of("duration", 30.0); // 30s is too short
        when(responseSpec.body(any(org.springframework.core.ParameterizedTypeReference.class))).thenReturn(responseMap);

        setRestClient(fileService, restClient);

        assertThatThrownBy(() -> fileService.confirm(11L, "audio"))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getErrorCode()).isEqualTo(FileErrorCode.INVALID_AUDIO_DURATION));
    }

    @Test
    void confirm_fileTooLarge_throws() throws Exception {
        FileMetadata metadata = new FileMetadata(1L, "music.mp3", "obj-1", "temp", "audio/mpeg", 0, FileStatus.UPLOADED);
        setId(metadata, 12L);
        when(repository.findById(12L)).thenReturn(Optional.of(metadata));

        io.minio.StatObjectResponse statResponse = mock(io.minio.StatObjectResponse.class);
        when(statResponse.size()).thenReturn(10L * 1024L * 1024L); // 10MB exceeds 5MB limit
        when(minioClient.statObject(any())).thenReturn(statResponse);

        assertThatThrownBy(() -> fileService.confirm(12L, "audio"))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getErrorCode()).isEqualTo(FileErrorCode.FILE_TOO_LARGE));
    }

    private void setId(FileMetadata metadata, Long id) {
        try {
            var field = FileMetadata.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(metadata, id);
        } catch (Exception ignored) {
        }
    }

    private void setRestClient(FileService service, org.springframework.web.client.RestClient restClient) {
        try {
            var field = FileService.class.getDeclaredField("restClient");
            field.setAccessible(true);
            field.set(service, restClient);
        } catch (Exception ignored) {
        }
    }
}
