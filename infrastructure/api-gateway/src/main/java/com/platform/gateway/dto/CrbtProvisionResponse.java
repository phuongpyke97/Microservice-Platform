package com.platform.gateway.dto;

import java.util.List;

public record CrbtProvisionResponse(Long userId, String msisdn, List<String> roles) {
}
