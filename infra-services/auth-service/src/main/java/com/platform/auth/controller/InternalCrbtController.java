package com.platform.auth.controller;

import com.platform.auth.dto.request.CrbtProvisionRequest;
import com.platform.auth.dto.response.CrbtProvisionResponse;
import com.platform.auth.dto.response.UserCreditInternalResponse;
import com.platform.auth.entity.User;
import com.platform.auth.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal endpoint called by API Gateway only.
 * Not routed externally — Gateway routes do not expose /internal/** paths.
 *
 * Called by CrbtTokenFilter after CRBT JWT is verified locally at Gateway.
 * Provisions (lazy-creates) a platform user for the CRBT subscriber.
 */
@RestController
@RequestMapping("/internal/crbt")
public class InternalCrbtController {

    private static final Logger log = LoggerFactory.getLogger(InternalCrbtController.class);

    private final AuthService authService;

    public InternalCrbtController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/provision")
    public ResponseEntity<CrbtProvisionResponse> provision(@RequestBody CrbtProvisionRequest request) {
        log.info("[CRBT-PROVISION] Start msisdn={}", mask(request.msisdn()));
        User user = authService.lazyCreateSubscriber(request.msisdn());
        log.info("[CRBT-PROVISION] Done userId={} msisdn={}", user.getId(), mask(request.msisdn()));
        return ResponseEntity.ok(new CrbtProvisionResponse(
                user.getId(),
                user.getMsisdn(),
                new ArrayList<>(user.getRoles())
        ));
    }

    @GetMapping("/user-credit/{msisdn}")
    public ResponseEntity<UserCreditInternalResponse> getUserCredit(@PathVariable String msisdn) {
        log.info("[CRBT-CREDIT-CHECK] msisdn={}", mask(msisdn));
        UserCreditInternalResponse resp = authService.getUserCredit(msisdn);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/users")
    public ResponseEntity<com.platform.common.core.response.PageResponse<com.platform.auth.dto.response.UserResponse>> getUsers(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String msisdn,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String status,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String startTime,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String endTime,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size) {
        org.springframework.data.domain.PageRequest pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return ResponseEntity.ok(authService.searchUsers(msisdn, status, startTime, endTime, pageable));
    }

    @GetMapping("/users/ids")
    public ResponseEntity<List<Long>> getUserIds(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String msisdn,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String status,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String startTime,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String endTime) {
        log.info("[CRBT-GET-USER-IDS] msisdn={}, status={}, startTime={}, endTime={}", mask(msisdn), status, startTime, endTime);
        return ResponseEntity.ok(authService.searchUserIds(msisdn, status, startTime, endTime));
    }

    @PostMapping("/users/msisdns")
    public ResponseEntity<java.util.Map<Long, String>> getMsisdnsByUserIds(@RequestBody List<Long> userIds) {
        return ResponseEntity.ok(authService.getMsisdnsByUserIds(userIds));
    }

    private String mask(String msisdn) {
        if (msisdn == null || msisdn.length() <= 4) return "***";
        return msisdn.substring(0, 3) + "***" + msisdn.substring(msisdn.length() - 2);
    }
}
