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
import com.platform.crbtcampaign.dto.response.SubscriptionResponse;
import com.platform.crbtcampaign.service.CampaignService;
import com.platform.crbtcampaign.service.MusicGenerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
        return ApiResponse.success(musicGenerationService.generate(msisdn, genre, mood, instrument));
    }
}
