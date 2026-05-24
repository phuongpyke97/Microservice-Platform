package com.platform.crbtcampaign.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record CreateCampaignRequest(
    @NotBlank @Size(max = 150) String name,
    @Size(max = 500) String description,
    @NotNull Instant startAt,
    @NotNull Instant endAt,
    @NotNull List<CreatePackageRequest> packages
) {}
