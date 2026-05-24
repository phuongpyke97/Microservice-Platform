package com.platform.crbtcoreadapter.controller;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.common.core.response.ApiResponse;
import com.platform.common.security.SecurityUtils;
import com.platform.crbtcoreadapter.dto.request.AssignRingtoneRequest;
import com.platform.crbtcoreadapter.dto.response.AssignmentResponse;
import com.platform.crbtcoreadapter.service.RingtoneAssignmentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ringtone-assignments")
public class RingtoneAssignmentController {

    private final RingtoneAssignmentService service;

    public RingtoneAssignmentController(RingtoneAssignmentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AssignmentResponse>> assign(@Valid @RequestBody AssignRingtoneRequest request) {
        Long userId = requireUserId();
        AssignmentResponse response = service.assign(userId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ApiResponse<List<AssignmentResponse>> list() {
        return ApiResponse.success(service.listByUser(requireUserId()));
    }

    @DeleteMapping("/{assignmentId}")
    public ApiResponse<AssignmentResponse> remove(@PathVariable Long assignmentId) {
        return ApiResponse.success(service.remove(requireUserId(), assignmentId));
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BaseException(CommonErrorCode.COMMON_UNAUTHORIZED);
        }
        return userId;
    }
}
