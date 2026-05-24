package com.platform.crbtcoreadapter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AssignRingtoneRequest(
    @NotBlank @Pattern(regexp = "^\\+?[0-9]{8,15}$") String msisdn,
    @NotBlank String ringtoneUrl
) {}
