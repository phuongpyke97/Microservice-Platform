package com.platform.crbtcampaign.controller;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.common.core.response.ApiResponse;
import com.platform.common.security.SecurityUtils;
import com.platform.crbtcampaign.dto.request.CreateCampaignRequest;
import com.platform.crbtcampaign.dto.request.RenewSubscriptionRequest;
import com.platform.crbtcampaign.dto.request.SubscribePackageRequest;
import com.platform.crbtcampaign.dto.response.CampaignResponse;
import com.platform.crbtcampaign.dto.response.GenerateMusicResponse;
import com.platform.crbtcampaign.dto.response.MyLibraryItemResponse;
import com.platform.crbtcampaign.dto.response.SubscriptionResponse;

import com.platform.crbtcampaign.service.CampaignService;
import com.platform.crbtcampaign.service.MusicGenerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/campaigns")
public class CampaignController {

    private final CampaignService campaignService;
    private final MusicGenerationService musicGenerationService;

    public CampaignController(CampaignService campaignService, MusicGenerationService musicGenerationService) {
        this.campaignService = campaignService;
        this.musicGenerationService = musicGenerationService;
    }

    @PostMapping
    public ApiResponse<CampaignResponse> createCampaign(@Valid @RequestBody CreateCampaignRequest request) {
        return ApiResponse.success(campaignService.createCampaign(request));
    }

    @GetMapping("/active")
    public ApiResponse<List<CampaignResponse>> getActiveCampaigns() {
        return ApiResponse.success(campaignService.getActiveCampaigns());
    }

    @PostMapping("/subscribe")
    public ApiResponse<Void> subscribe(@Valid @RequestBody SubscribePackageRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BaseException(CommonErrorCode.COMMON_UNAUTHORIZED);
        }
        campaignService.subscribe(userId, request.packageId(), Boolean.TRUE.equals(request.confirmChange()));
        return ApiResponse.success(null);
    }

    @PostMapping("/unsubscribe")
    public ApiResponse<Void> unsubscribe() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BaseException(CommonErrorCode.COMMON_UNAUTHORIZED);
        }
        campaignService.unsubscribe(userId);
        return ApiResponse.success(null);
    }

    @GetMapping("/subscriptions/active")
    public ApiResponse<SubscriptionResponse> getActiveSubscription() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BaseException(CommonErrorCode.COMMON_UNAUTHORIZED);
        }
        return ApiResponse.success(campaignService.getActiveSubscription(userId));
    }

    @PostMapping("/internal/crbt/renew")
    public ApiResponse<Void> renew(@Valid @RequestBody RenewSubscriptionRequest request) {
        campaignService.renewSubscriptionInternal(request);
        return ApiResponse.success(null);
    }

    @PostMapping("/generate")
    public ApiResponse<GenerateMusicResponse> generate(
            @RequestHeader(value = "X-MSISDN", required = false) String msisdn,
            @RequestParam @NotBlank @Size(max = 50) String genre,
            @RequestParam @NotBlank @Size(max = 50) String mood,
            @RequestParam(required = false, defaultValue = "") @Size(max = 50) String instrument) {
        if (msisdn == null || msisdn.isBlank()) {
            throw new BaseException(CommonErrorCode.COMMON_UNAUTHORIZED);
        }

        // Pre-populate audit log metadata with MSISDN and model to handle failures or cache hits
        try {
            org.springframework.web.context.request.ServletRequestAttributes attributes =
                (org.springframework.web.context.request.ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                jakarta.servlet.http.HttpServletRequest request = attributes.getRequest();
                Map<String, Object> initialTokenUsage = new HashMap<>();
                initialTokenUsage.put("msisdn", msisdn);
                initialTokenUsage.put("model", "lyria-3-clip-preview");
                initialTokenUsage.put("prompt_tokens", 0);
                initialTokenUsage.put("candidate_tokens", 0);
                initialTokenUsage.put("total_tokens", 0);
                initialTokenUsage.put("genre", genre);
                initialTokenUsage.put("mood", mood);
                initialTokenUsage.put("instrument", instrument);
                request.setAttribute("lyria_token_usage", initialTokenUsage);
            }
        } catch (Exception ex) {
            // Ignore context issues (e.g. non-web context tests)
        }

        return ApiResponse.success(musicGenerationService.generate(msisdn, genre, mood, instrument));
    }

    @GetMapping("/my-library")
    public ApiResponse<com.platform.common.core.response.PageResponse<MyLibraryItemResponse>> getMyLibrary(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BaseException(CommonErrorCode.COMMON_UNAUTHORIZED);
        }
        return ApiResponse.success(musicGenerationService.getMyLibrary(userId, search, source, page, size));
    }

    @DeleteMapping("/my-library/{unifiedId}")
    public ApiResponse<Void> deleteLibraryItem(@PathVariable String unifiedId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BaseException(CommonErrorCode.COMMON_UNAUTHORIZED);
        }
        musicGenerationService.deleteLibraryItem(userId, unifiedId);
        return ApiResponse.success(null);
    }

    @PutMapping("/my-library/{unifiedId}")
    public ApiResponse<MyLibraryItemResponse> updateLibraryItem(
            @PathVariable String unifiedId,
            @RequestBody MyLibraryItemResponse request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BaseException(CommonErrorCode.COMMON_UNAUTHORIZED);
        }
        return ApiResponse.success(musicGenerationService.updateLibraryItem(userId, unifiedId, request));
    }
}

