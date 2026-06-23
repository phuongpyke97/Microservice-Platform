package com.platform.crbtcampaign.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateLyriaPromptRequest(
    @NotBlank String model,
    @NotBlank String promptTemplate,
    @NotEmpty List<String> keys,
    @NotEmpty List<String> secondaryInstrumentations,
    @NotEmpty List<String> tempoGrooves,
    @NotEmpty List<String> acousticEnvironments
) {}
