package com.platform.crbtcampaign.controller;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.common.core.response.ApiResponse;
import com.platform.common.security.SecurityUtils;
import com.platform.crbtcampaign.dto.request.CreateCampaignRequest;
import com.platform.crbtcampaign.dto.request.SubscribePackageRequest;
import com.platform.crbtcampaign.dto.response.CampaignResponse;
import com.platform.crbtcampaign.service.CampaignService;
import com.platform.crbtcampaign.service.LyriaService;
import jakarta.validation.Valid;
import java.util.List;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/campaigns")
public class CampaignController {

    private final CampaignService campaignService;
    private final LyriaService lyriaService;

    public CampaignController(CampaignService campaignService, LyriaService lyriaService) {
        this.campaignService = campaignService;
        this.lyriaService = lyriaService;
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
        campaignService.subscribe(userId, request.packageId());
        return ApiResponse.success(null);
    }

    @PostMapping("/generate")
    public ApiResponse<byte[]> generate(@RequestParam String genre, 
                                       @RequestParam String mood, 
                                       @RequestParam String instrument) {
        return ApiResponse.success(lyriaService.generateMusic(genre, mood, instrument));
    }
}
