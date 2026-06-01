package com.platform.fileservice.controller;

import com.platform.common.core.response.ApiResponse;
import com.platform.common.security.SecurityUtils;
import com.platform.fileservice.dto.request.ConfirmFileRequest;
import com.platform.fileservice.dto.response.FileMetadataResponse;
import com.platform.fileservice.dto.response.PresignedUrlResponse;
import com.platform.fileservice.service.FileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<FileMetadataResponse>> upload(@RequestPart("file") MultipartFile file) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("File uploaded", fileService.uploadTemp(userId, file)));
    }

    @GetMapping("/presigned/upload")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> getUploadUrl(@RequestParam String originalName,
                                                                          @RequestParam String contentType) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(fileService.getUploadUrl(userId, originalName, contentType)));
    }

    @GetMapping("/{fileId}/presigned/download")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> getDownloadUrl(@PathVariable Long fileId) {
        return ResponseEntity.ok(ApiResponse.success(fileService.getDownloadUrl(fileId)));
    }

    @GetMapping("/{fileId}/internal/presigned/download")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> getInternalDownloadUrl(@PathVariable Long fileId) {
        return ResponseEntity.ok(ApiResponse.success(fileService.getInternalDownloadUrl(fileId)));
    }

    @GetMapping("/{fileId}/internal/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long fileId) {
        byte[] data = fileService.downloadFile(fileId);
        return ResponseEntity.ok()
                .header("Content-Type", "application/octet-stream")
                .body(data);
    }

    @PostMapping("/{fileId}/confirm")
    public ResponseEntity<ApiResponse<FileMetadataResponse>> confirm(@PathVariable Long fileId,
                                                                     @Valid @RequestBody ConfirmFileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("File confirmed", fileService.confirm(fileId, request.targetBucket())));
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<ApiResponse<FileMetadataResponse>> delete(@PathVariable Long fileId) {
        return ResponseEntity.ok(ApiResponse.success("File deleted", fileService.softDelete(fileId)));
    }

    @PostMapping(value = "/internal/upload-audio", consumes = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<ApiResponse<String>> uploadAudio(
            @RequestBody byte[] audioBytes,
            @RequestParam(defaultValue = "media-audio") String bucket) {
        String url = fileService.uploadAudioBytes(audioBytes, bucket);
        return ResponseEntity.ok(ApiResponse.success(url));
    }
}
