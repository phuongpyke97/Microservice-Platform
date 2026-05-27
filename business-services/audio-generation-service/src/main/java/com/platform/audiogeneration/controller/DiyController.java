package com.platform.audiogeneration.controller;

import com.platform.audiogeneration.dto.request.GenerateAudioRequest;
import com.platform.audiogeneration.dto.response.AudioJobResponse;
import com.platform.audiogeneration.service.AudioGenerationService;
import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.common.core.response.ApiResponse;
import com.platform.common.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/diy")
public class DiyController {

    private final AudioGenerationService service;

    public DiyController(AudioGenerationService service) {
        this.service = service;
    }

    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> analyze(
            @RequestParam(value = "file", required = false) org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "audioFileKey", required = false) String audioFileKey,
            @RequestParam(value = "skipVocal", required = false, defaultValue = "false") Boolean skipVocal) {
        
        if (file != null && !file.isEmpty()) {
            java.util.Map<String, Object> result = service.analyzeAudio(file, skipVocal);
            return ResponseEntity.ok(ApiResponse.success(result));
        } else if (audioFileKey != null && !audioFileKey.isBlank()) {
            java.util.Map<String, Object> result = service.analyzeAudioFromKey(audioFileKey, skipVocal);
            return ResponseEntity.ok(ApiResponse.success(result));
        } else {
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Either file or audioFileKey must be provided");
        }
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<AudioJobResponse>> generate(@Valid @RequestBody GenerateAudioRequest request) {
        // Enforce DIY job type
        GenerateAudioRequest diyRequest = new GenerateAudioRequest(
            request.prompt(),
            request.voiceId(),
            "DIY",
            request.audioFileKey(),
            request.vocalStart(),
            request.vocalEnd()
        );
        Long userId = requireUserId();
        AudioJobResponse response = service.submitJob(userId, diyRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BaseException(CommonErrorCode.COMMON_UNAUTHORIZED);
        }
        return userId;
    }
}
