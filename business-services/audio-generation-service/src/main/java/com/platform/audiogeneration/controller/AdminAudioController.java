package com.platform.audiogeneration.controller;

import com.platform.audiogeneration.dto.request.GenerateAudioRequest;
import com.platform.audiogeneration.dto.response.AudioJobResponse;
import com.platform.audiogeneration.service.AudioGenerationService;
import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.common.core.response.ApiResponse;
import com.platform.common.security.SecurityUtils;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/audio-jobs/admin")
public class AdminAudioController {

    private final AudioGenerationService service;

    public AdminAudioController(AudioGenerationService service) {
        this.service = service;
    }

    @GetMapping("/search")
    public ApiResponse<List<AudioJobResponse>> search(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String msisdn,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        requireAdminRole();
        Instant start = (startTime != null && !startTime.isBlank()) ? Instant.parse(startTime) : null;
        Instant end = (endTime != null && !endTime.isBlank()) ? Instant.parse(endTime) : null;
        Page<AudioJobResponse> result = service.searchJobsAdmin(start, end, userId, msisdn, search, PageRequest.of(page, size));
        return ApiResponse.success(result.getContent());
    }

    @GetMapping("/{jobId}")
    public ApiResponse<AudioJobResponse> get(@PathVariable Long jobId) {
        requireAdminRole();
        return ApiResponse.success(service.getJobAdmin(jobId));
    }

    @PostMapping
    public ApiResponse<AudioJobResponse> create(
            @RequestParam(required = false) Long userId,
            @RequestBody GenerateAudioRequest request) {
        requireAdminRole();
        return ApiResponse.success(service.createJobAdmin(userId, request));
    }

    @PutMapping("/{jobId}")
    public ApiResponse<AudioJobResponse> update(
            @PathVariable Long jobId,
            @RequestBody AudioJobResponse request) {
        requireAdminRole();
        return ApiResponse.success(service.updateJobAdmin(jobId, request));
    }

    @DeleteMapping("/{jobId}")
    public ApiResponse<Void> delete(
            @PathVariable Long jobId,
            @RequestParam(defaultValue = "false") boolean hard) {
        requireAdminRole();
        service.deleteJobAdmin(jobId, hard);
        return ApiResponse.success(null);
    }

    private void requireAdminRole() {
        List<String> roles = SecurityUtils.getCurrentUserRoles();
        if (roles == null || !roles.contains("ADMIN")) {
            throw new BaseException(CommonErrorCode.COMMON_FORBIDDEN, "Access denied. Admin role required.");
        }
    }
}
