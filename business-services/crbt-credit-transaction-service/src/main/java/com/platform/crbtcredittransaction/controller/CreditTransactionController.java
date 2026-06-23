package com.platform.crbtcredittransaction.controller;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.common.core.response.ApiResponse;
import com.platform.common.core.response.PageResponse;
import com.platform.common.security.SecurityUtils;
import com.platform.crbtcredittransaction.dto.response.CreditTransactionResponse;
import com.platform.crbtcredittransaction.service.CreditTransactionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;
import com.platform.crbtcredittransaction.dto.response.UserCreditStats;
import com.platform.crbtcredittransaction.dto.response.CreditTransactionPageWithStats;
import com.platform.crbtcredittransaction.dto.response.CreditTransactionStats;

@RestController
@RequestMapping("/credit-transactions")
public class CreditTransactionController {

    private final CreditTransactionService creditTransactionService;
    private final org.springframework.web.client.RestTemplate restTemplate;

    public CreditTransactionController(CreditTransactionService creditTransactionService, org.springframework.web.client.RestTemplate restTemplate) {
        this.creditTransactionService = creditTransactionService;
        this.restTemplate = restTemplate;
    }

    private Long parseTimestamp(String tsStr, boolean isEndOfDay) {
        if (tsStr == null || tsStr.isBlank()) {
            return null;
        }
        try {
            tsStr = tsStr.trim();
            if (tsStr.matches("^\\d+$")) {
                return Long.parseLong(tsStr);
            }
            if (tsStr.endsWith("Z") || tsStr.contains("+") || (tsStr.contains("-") && tsStr.lastIndexOf("-") > 7 && tsStr.contains("T") && (tsStr.contains(":") && (tsStr.contains("+") || tsStr.substring(tsStr.lastIndexOf(":")).contains("-"))))) {
                return java.time.Instant.parse(tsStr).toEpochMilli();
            }
            if (tsStr.contains(" ") && !tsStr.contains("T")) {
                tsStr = tsStr.replace(" ", "T");
            }
            if (tsStr.contains("T")) {
                return java.time.LocalDateTime.parse(tsStr).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            } else {
                java.time.LocalDate date = java.time.LocalDate.parse(tsStr);
                if (isEndOfDay) {
                    return date.atTime(23, 59, 59, 999999999).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                } else {
                    return date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                }
            }
        } catch (Exception e) {
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Invalid timestamp format: " + tsStr);
        }
    }

    private Long resolveUserIdByMsisdn(String msisdn) {
        if (msisdn == null || msisdn.isBlank()) {
            return null;
        }
        try {
            Map<?, ?> response = restTemplate.getForObject("http://auth-service/internal/crbt/user-credit/" + msisdn, Map.class);
            if (response != null && response.get("userId") != null) {
                return ((Number) response.get("userId")).longValue();
            }
        } catch (Exception e) {
            return -1L;
        }
        return -1L;
    }

    @GetMapping("/history")
    public ApiResponse<CreditTransactionPageWithStats> getCreditTransactionHistory(
            @RequestParam(required = false) String msisdn,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String fromTs,
            @RequestParam(required = false) String toTs,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BaseException(CommonErrorCode.COMMON_UNAUTHORIZED);
        }

        List<String> roles = SecurityUtils.getCurrentUserRoles();
        boolean isAdmin = roles != null && roles.contains("ADMIN");

        Long targetUserId;
        if (msisdn != null && !msisdn.isBlank()) {
            if (isAdmin) {
                targetUserId = resolveUserIdByMsisdn(msisdn);
            } else {
                throw new BaseException(CommonErrorCode.COMMON_FORBIDDEN, "Access denied. Only admin can query other user's history.");
            }
        } else {
            targetUserId = isAdmin ? null : currentUserId;
        }

        Long fromTime = parseTimestamp(fromTs, false);
        Long toTime = parseTimestamp(toTs, true);

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return ApiResponse.success(creditTransactionService.queryWithStats(targetUserId, direction, reason, fromTime, toTime, pageable));
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportCsv(
            @RequestParam(required = false) String msisdn,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String fromTs,
            @RequestParam(required = false) String toTs) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BaseException(CommonErrorCode.COMMON_UNAUTHORIZED);
        }

        List<String> roles = SecurityUtils.getCurrentUserRoles();
        boolean isAdmin = roles != null && roles.contains("ADMIN");

        Long targetUserId;
        if (msisdn != null && !msisdn.isBlank()) {
            if (isAdmin) {
                targetUserId = resolveUserIdByMsisdn(msisdn);
            } else {
                throw new BaseException(CommonErrorCode.COMMON_FORBIDDEN, "Access denied. Only admin can export other user's history.");
            }
        } else {
            targetUserId = isAdmin ? null : currentUserId;
        }

        Long fromTime = parseTimestamp(fromTs, false);
        Long toTime = parseTimestamp(toTs, true);

        String csv = creditTransactionService.exportCsv(targetUserId, direction, reason, fromTime, toTime);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "credit_transactions.csv");

        return ResponseEntity.ok().headers(headers).body(csv);
    }

    @PostMapping("/internal/stats")
    public ApiResponse<Map<Long, UserCreditStats>> getStats(@RequestBody List<Long> userIds) {
        return ApiResponse.success(creditTransactionService.getStatsByUserIds(userIds));
    }

    @PostMapping("/internal/stats/sum")
    public ApiResponse<UserCreditStats> sumStats(@RequestBody List<Long> userIds) {
        return ApiResponse.success(creditTransactionService.sumStatsByUserIds(userIds));
    }
}
