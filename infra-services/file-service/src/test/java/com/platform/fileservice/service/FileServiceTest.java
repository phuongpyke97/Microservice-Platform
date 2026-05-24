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

    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileService(
                repository,
                minioClient,
                new MinioProperties("http://localhost:9000", "minio", "minio123", "temp", "audio", "image")
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
        when(minioClient.getPresignedObjectUrl(any())).thenReturn("http://minio/upload");

        PresignedUrlResponse response = fileService.getUploadUrl("a.png", "image/png");

        assertThat(response.url()).isEqualTo("http://minio/upload");
        assertThat(response.expiresInSeconds()).isEqualTo(300L);
    }

    @Test
    void confirm_uploadedTempFile_movesToTargetBucket() throws Exception {
        FileMetadata metadata = new FileMetadata(1L, "a.png", "obj-1", "temp", "image/png", 10, FileStatus.UPLOADED);
        setId(metadata, 3L);
        when(repository.findById(3L)).thenReturn(Optional.of(metadata));
        when(repository.save(any(FileMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));

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

    private void setId(FileMetadata metadata, Long id) {
        try {
            var field = FileMetadata.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(metadata, id);
        } catch (Exception ignored) {
        }
    }
}
