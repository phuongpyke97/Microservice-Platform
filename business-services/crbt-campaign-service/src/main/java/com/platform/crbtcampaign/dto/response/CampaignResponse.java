package com.platform.crbtcampaign.dto.response;

import com.platform.crbtcampaign.entity.Campaign;
import java.time.Instant;
import java.util.List;

public record CampaignResponse(
    Long id,
    String name,
    String description,
    Campaign.Status status,
    Instant startAt,
    Instant endAt,
    List<PackageResponse> packages
) {}
