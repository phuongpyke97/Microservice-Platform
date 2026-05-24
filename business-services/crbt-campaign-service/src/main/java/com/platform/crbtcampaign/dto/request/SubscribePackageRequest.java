package com.platform.crbtcampaign.dto.request;

import jakarta.validation.constraints.NotNull;

public record SubscribePackageRequest(@NotNull Long packageId) {}
