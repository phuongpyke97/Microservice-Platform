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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/credit-transactions")
public class CreditTransactionController {

    private final CreditTransactionService creditTransactionService;

    public CreditTransactionController(CreditTransactionService creditTransactionService) {
        this.creditTransactionService = creditTransactionService;
    }

    @GetMapping("/history")
    public ApiResponse<PageResponse<CreditTransactionResponse>> getCreditTransactionHistory(
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) Long fromTs,
            @RequestParam(required = false) Long toTs,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BaseException(CommonErrorCode.COMMON_UNAUTHORIZED);
        }

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return ApiResponse.success(creditTransactionService.query(userId, direction, reason, fromTs, toTs, pageable));
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportCsv(
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) Long fromTs,
            @RequestParam(required = false) Long toTs) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BaseException(CommonErrorCode.COMMON_UNAUTHORIZED);
        }

        String csv = creditTransactionService.exportCsv(userId, direction, reason, fromTs, toTs);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "credit_transactions.csv");

        return ResponseEntity.ok().headers(headers).body(csv);
    }
}
