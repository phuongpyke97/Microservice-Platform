package com.platform.crbtcoreadapter.controller;

import com.platform.common.core.response.ApiResponse;
import com.platform.crbtcoreadapter.service.RingtoneAssignmentService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/internal/ringtone-assignments")
public class InternalRingtoneAssignmentController {

    private final RingtoneAssignmentService service;

    public InternalRingtoneAssignmentController(RingtoneAssignmentService service) {
        this.service = service;
    }

    @PostMapping("/active-check")
    public ApiResponse<List<String>> activeCheck(@RequestBody List<String> urls) {
        return ApiResponse.success("Active ringtone URLs check completed", service.getActiveRingtoneUrls(urls));
    }
}
