package com.platform.crbtcampaign.controller;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.common.core.response.ApiResponse;
import com.platform.common.core.response.PageResponse;
import com.platform.common.security.SecurityUtils;
import com.platform.crbtcampaign.dto.response.UserCreditSummaryResponse;
import com.platform.crbtcampaign.dto.response.UserCreditSummaryPageWithStats;
import com.platform.crbtcampaign.service.AdminUserService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/campaigns/admin/users-credit-summary")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<UserCreditSummaryPageWithStats> getUsersCreditSummary(
            @RequestParam(required = false) String msisdn,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String packageName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdminRole();
        UserCreditSummaryPageWithStats result = adminUserService.getUsersCreditSummary(
            msisdn, status, packageName, page, size
        );
        return ApiResponse.success(result);
    }

    private void requireAdminRole() {
        List<String> roles = SecurityUtils.getCurrentUserRoles();
        if (roles == null || !roles.contains("ADMIN")) {
            throw new BaseException(CommonErrorCode.COMMON_FORBIDDEN, "Access denied. Admin role required.");
        }
    }
}
