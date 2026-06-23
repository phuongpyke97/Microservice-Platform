package com.platform.crbtcampaign.controller;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.common.core.response.ApiResponse;
import com.platform.common.security.SecurityUtils;
import com.platform.crbtcampaign.dto.request.UpdateLyriaPromptRequest;
import com.platform.crbtcampaign.dto.response.LyriaPromptResponse;
import com.platform.crbtcampaign.dto.response.LyriaPromptVersionResponse;
import com.platform.crbtcampaign.entity.LyriaPromptConfig;
import com.platform.crbtcampaign.service.LyriaPromptAdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/campaigns/admin/lyria-prompts")
public class AdminLyriaPromptController {

    private final LyriaPromptAdminService adminService;

    public AdminLyriaPromptController(LyriaPromptAdminService adminService) {
        this.adminService = adminService;
    }

    /** Active version of a model. */
    @GetMapping("/active")
    public ApiResponse<LyriaPromptResponse> getActive(
            @RequestParam(required = false, defaultValue = LyriaPromptConfig.DEFAULT_MODEL) String model) {
        requireAdminRole();
        return ApiResponse.success(adminService.getActive(model));
    }

    /** Version-history rows. model = ALL or a specific model. */
    @GetMapping("/history")
    public ApiResponse<List<LyriaPromptVersionResponse>> getHistory(
            @RequestParam(required = false, defaultValue = "ALL") String model) {
        requireAdminRole();
        return ApiResponse.success(adminService.listHistory(model));
    }

    /** Full payload of one specific version. */
    @GetMapping("/versions/{model}/{version}")
    public ApiResponse<LyriaPromptResponse> getVersion(@PathVariable String model, @PathVariable int version) {
        requireAdminRole();
        return ApiResponse.success(adminService.getVersion(model, version));
    }

    /** Save new version (per model) and activate it. */
    @PostMapping
    public ApiResponse<LyriaPromptResponse> saveNewVersion(@Valid @RequestBody UpdateLyriaPromptRequest request) {
        requireAdminRole();
        return ApiResponse.success(adminService.saveNewVersion(request));
    }

    /** Activate an existing past version of a model. */
    @PutMapping("/versions/{model}/{version}/activate")
    public ApiResponse<LyriaPromptResponse> activateVersion(@PathVariable String model, @PathVariable int version) {
        requireAdminRole();
        return ApiResponse.success(adminService.activateVersion(model, version));
    }

    private void requireAdminRole() {
        List<String> roles = SecurityUtils.getCurrentUserRoles();
        if (roles == null || !roles.contains("ADMIN")) {
            throw new BaseException(CommonErrorCode.COMMON_FORBIDDEN, "Access denied. Admin role required.");
        }
    }
}
