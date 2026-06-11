package com.platform.crbtcampaign.controller;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.common.core.response.ApiResponse;
import com.platform.common.core.response.PageResponse;
import com.platform.common.security.SecurityUtils;
import com.platform.crbtcampaign.dto.response.MyLibraryItemResponse;
import com.platform.crbtcampaign.service.MusicGenerationService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/campaigns/admin/music-items")
public class AdminMusicController {

    private final MusicGenerationService musicGenerationService;

    public AdminMusicController(MusicGenerationService musicGenerationService) {
        this.musicGenerationService = musicGenerationService;
    }

    @GetMapping
    public ApiResponse<PageResponse<MyLibraryItemResponse>> search(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String msisdn,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        requireAdminRole();
        PageResponse<MyLibraryItemResponse> result = musicGenerationService.searchMusicItemsAdmin(
            startTime, endTime, source, userId, msisdn, search, page, size
        );
        return ApiResponse.success(result);
    }

    @GetMapping("/{unifiedId}")
    public ApiResponse<MyLibraryItemResponse> get(@PathVariable String unifiedId) {
        requireAdminRole();
        return ApiResponse.success(musicGenerationService.getMusicItemAdmin(unifiedId));
    }

    @PostMapping
    public ApiResponse<MyLibraryItemResponse> create(@RequestBody MyLibraryItemResponse request) {
        requireAdminRole();
        return ApiResponse.success(musicGenerationService.createMusicItemAdmin(request));
    }

    @PutMapping("/{unifiedId}")
    public ApiResponse<MyLibraryItemResponse> update(
            @PathVariable String unifiedId,
            @RequestBody MyLibraryItemResponse request) {
        requireAdminRole();
        return ApiResponse.success(musicGenerationService.updateMusicItemAdmin(unifiedId, request));
    }

    @DeleteMapping("/{unifiedId}")
    public ApiResponse<Void> delete(
            @PathVariable String unifiedId,
            @RequestParam(defaultValue = "false") boolean hard) {
        requireAdminRole();
        musicGenerationService.deleteMusicItemAdmin(unifiedId, hard);
        return ApiResponse.success(null);
    }

    private void requireAdminRole() {
        List<String> roles = SecurityUtils.getCurrentUserRoles();
        if (roles == null || !roles.contains("ADMIN")) {
            throw new BaseException(CommonErrorCode.COMMON_FORBIDDEN, "Access denied. Admin role required.");
        }
    }
}
