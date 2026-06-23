package com.platform.creditwallet.controller;

import com.platform.common.core.response.ApiResponse;
import com.platform.common.security.SecurityUtils;
import com.platform.creditwallet.dto.request.AmountRequest;
import com.platform.creditwallet.dto.response.WalletResponse;
import com.platform.creditwallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<WalletResponse>> getMyWallet() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(walletService.getBalance(userId)));
    }

    @GetMapping("/internal/{userId}/balance")
    public ResponseEntity<ApiResponse<WalletResponse>> getBalanceInternal(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(walletService.getBalance(userId)));
    }

    @PostMapping("/{userId}/deduct")
    public ResponseEntity<ApiResponse<WalletResponse>> deduct(@PathVariable Long userId,
                                                              @Valid @RequestBody AmountRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Credit deducted",
                walletService.deductCredit(userId, request.amount(), request.reason(), request.referenceId(),
                        request.isFree() != null ? request.isFree() : false,
                        request.genType() != null ? request.genType() : "OTHER",
                        request.model())));
    }

    @PostMapping("/{userId}/add")
    public ResponseEntity<ApiResponse<WalletResponse>> add(@PathVariable Long userId,
                                                           @Valid @RequestBody AmountRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Credit added",
                walletService.addCredit(userId, request.amount(), request.reason(), request.referenceId(),
                        request.isFree() != null ? request.isFree() : false,
                        request.genType() != null ? request.genType() : "OTHER",
                        request.model())));
    }
}
