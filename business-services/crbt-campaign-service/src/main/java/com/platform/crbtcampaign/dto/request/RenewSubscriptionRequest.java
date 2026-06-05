package com.platform.crbtcampaign.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record RenewSubscriptionRequest(
    @NotBlank String msisdn,
    @NotBlank String packageCode,
    @NotNull Instant expiresAt
) {}
