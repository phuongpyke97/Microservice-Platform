package com.platform.payment.service;

import com.platform.common.core.exception.BaseException;
import com.platform.common.rmq.RmqExchanges;
import com.platform.common.rmq.RmqRoutingKeys;
import com.platform.common.rmq.event.PaymentResultEvent;
import com.platform.payment.client.MpsChargeRequest;
import com.platform.payment.client.MpsChargeResponse;
import com.platform.payment.client.MpsClient;
import com.platform.payment.dto.request.ChargeRequest;
import com.platform.payment.dto.response.PaymentResponse;
import com.platform.payment.entity.PaymentStatus;
import com.platform.payment.entity.PaymentTransaction;
import com.platform.payment.exception.PaymentErrorCode;
import com.platform.payment.repository.PaymentTransactionRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentTransactionRepository repository;
    private final MpsClient mpsClient;
    private final RabbitTemplate rabbitTemplate;

    public PaymentService(PaymentTransactionRepository repository, MpsClient mpsClient, RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.mpsClient = mpsClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    @CircuitBreaker(name = "mpsCharge", fallbackMethod = "chargeFallback")
    public PaymentResponse charge(Long userId, String msisdn, ChargeRequest request) {
        if (request.amountMmk() <= 0 || request.creditAmount() <= 0) {
            throw new BaseException(PaymentErrorCode.PAY_INVALID_AMOUNT);
        }

        var existing = repository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        PaymentTransaction tx = repository.save(new PaymentTransaction(
                request.idempotencyKey(), userId, msisdn, request.packageCode(), request.amountMmk(), request.creditAmount()
        ));

        MpsChargeResponse providerResponse = mpsClient.charge(
                new MpsChargeRequest(msisdn, request.amountMmk(), request.packageCode(), request.idempotencyKey())
        );

        if (providerResponse.success()) {
            tx.markSuccess(providerResponse.providerReference());
        } else {
            tx.markFailed(providerResponse.message());
        }
        PaymentTransaction saved = repository.save(tx);
        publishResult(saved);
        log.info("payment reconciliation txId={} status={} idempotencyKey={}", saved.getId(), saved.getStatus(), saved.getIdempotencyKey());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey)
                .map(this::toResponse)
                .orElseThrow(() -> new BaseException(PaymentErrorCode.PAY_TRANSACTION_NOT_FOUND));
    }

    private PaymentResponse chargeFallback(Long userId, String msisdn, ChargeRequest request, Throwable throwable) {
        throw new BaseException(PaymentErrorCode.PAY_MPS_UNAVAILABLE);
    }

    private void publishResult(PaymentTransaction tx) {
        rabbitTemplate.convertAndSend(
                RmqExchanges.PAYMENT_EVENTS,
                RmqRoutingKeys.PAYMENT_RESULT,
                new PaymentResultEvent(
                        tx.getUserId(),
                        String.valueOf(tx.getId()),
                        tx.getPackageCode(),
                        tx.getStatus().name(),
                        tx.getStatus() == PaymentStatus.SUCCESS ? tx.getCreditAmount() : 0,
                        Instant.now().toEpochMilli()
                )
        );
    }

    private PaymentResponse toResponse(PaymentTransaction tx) {
        return new PaymentResponse(
                tx.getId(),
                tx.getIdempotencyKey(),
                tx.getStatus(),
                tx.getMpsRef(),
                tx.getPackageCode(),
                tx.getAmountMmk(),
                tx.getCreditAmount()
        );
    }
}
