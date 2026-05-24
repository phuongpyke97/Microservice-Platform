package com.platform.audiogeneration.controller;

import com.platform.audiogeneration.dto.request.GenerateAudioRequest;
import com.platform.audiogeneration.dto.response.AudioJobResponse;
import com.platform.audiogeneration.service.AudioGenerationService;
import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.common.core.response.ApiResponse;
import com.platform.common.security.SecurityUtils;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/audio-jobs")
public class AudioGenerationController {

    private final AudioGenerationService service;

    public AudioGenerationController(AudioGenerationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AudioJobResponse>> submit(@Valid @RequestBody GenerateAudioRequest request) {
        Long userId = requireUserId();
        AudioJobResponse response = service.submitJob(userId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ApiResponse<List<AudioJobResponse>> list() {
        return ApiResponse.success(service.getUserJobs(requireUserId()));
    }

    @GetMapping("/{jobId}")
    public ApiResponse<AudioJobResponse> get(@PathVariable Long jobId) {
        return ApiResponse.success(service.getJob(jobId, requireUserId()));
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BaseException(CommonErrorCode.COMMON_UNAUTHORIZED);
        }
        return userId;
    }
}
