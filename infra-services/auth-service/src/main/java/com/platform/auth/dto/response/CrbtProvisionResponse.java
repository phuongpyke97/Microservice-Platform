package com.platform.auth.dto.response;

import java.util.List;

public record CrbtProvisionResponse(Long userId, String msisdn, List<String> roles) {
}
