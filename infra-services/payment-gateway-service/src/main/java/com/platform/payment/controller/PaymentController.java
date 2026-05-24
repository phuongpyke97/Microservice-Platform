package com.platform.payment.controller;

import com.platform.common.core.response.ApiResponse;
import com.platform.common.security.SecurityUtils;
import com.platform.payment.dto.request.ChargeRequest;
import com.platform.payment.dto.response.PaymentResponse;
import com.platform.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/charge")
    public ResponseEntity<ApiResponse<PaymentResponse>> charge(@Valid @RequestBody ChargeRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        String msisdn = SecurityUtils.getCurrentMsisdn();
        return ResponseEntity.ok(ApiResponse.success("Payment processed", paymentService.charge(userId, msisdn, request)));
    }

    @GetMapping("/idempotency/{idempotencyKey}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getByIdempotencyKey(@PathVariable String idempotencyKey) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getByIdempotencyKey(idempotencyKey)));
    }
}
