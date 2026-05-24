package com.platform.creditwallet.service;

import com.platform.common.core.exception.BaseException;
import com.platform.common.rmq.RmqExchanges;
import com.platform.common.rmq.RmqRoutingKeys;
import com.platform.common.rmq.event.CreditChangedEvent;
import com.platform.creditwallet.dto.response.WalletResponse;
import com.platform.creditwallet.entity.Wallet;
import com.platform.creditwallet.exception.WalletErrorCode;
import com.platform.creditwallet.repository.WalletRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class WalletService {

    private static final String LOCK_KEY_PREFIX = "wallet:";
    private static final long LOCK_TIMEOUT_SECONDS = 3;

    private final WalletRepository walletRepository;
    private final RedissonClient redissonClient;
    private final RabbitTemplate rabbitTemplate;

    public WalletService(WalletRepository walletRepository, RedissonClient redissonClient,
                         RabbitTemplate rabbitTemplate) {
        this.walletRepository = walletRepository;
        this.redissonClient = redissonClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional(readOnly = true)
    public WalletResponse getBalance(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BaseException(WalletErrorCode.WALLET_NOT_FOUND));
        return new WalletResponse(userId, wallet.getBalance());
    }

    @Transactional
    public WalletResponse deductCredit(Long userId, int amount, String reason, String referenceId) {
        if (amount <= 0) throw new BaseException(WalletErrorCode.WALLET_INVALID_AMOUNT);

        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + userId);
        try {
            boolean acquired = lock.tryLock(LOCK_TIMEOUT_SECONDS, LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) throw new BaseException(WalletErrorCode.WALLET_LOCK_TIMEOUT);

            try {
                Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                        .orElseThrow(() -> new BaseException(WalletErrorCode.WALLET_NOT_FOUND));
                if (wallet.getBalance() < amount) {
                    throw new BaseException(WalletErrorCode.WALLET_INSUFFICIENT_CREDIT);
                }
                wallet.deductBalance(amount);
                walletRepository.save(wallet);

                rabbitTemplate.convertAndSend(
                        RmqExchanges.CREDIT_EVENTS,
                        RmqRoutingKeys.CREDIT_DEDUCTED,
                        new CreditChangedEvent(userId, amount, "DEDUCT", reason, referenceId,
                                Instant.now().toEpochMilli())
                );
                return new WalletResponse(userId, wallet.getBalance());
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException(WalletErrorCode.WALLET_LOCK_TIMEOUT);
        }
    }

    @Transactional
    public WalletResponse addCredit(Long userId, int amount, String reason, String referenceId) {
        if (amount <= 0) throw new BaseException(WalletErrorCode.WALLET_INVALID_AMOUNT);

        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + userId);
        try {
            boolean acquired = lock.tryLock(LOCK_TIMEOUT_SECONDS, LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) throw new BaseException(WalletErrorCode.WALLET_LOCK_TIMEOUT);

            try {
                Wallet wallet = walletRepository.findByUserId(userId)
                        .orElseGet(() -> walletRepository.save(new Wallet(userId, 0)));
                wallet.addBalance(amount);
                walletRepository.save(wallet);

                rabbitTemplate.convertAndSend(
                        RmqExchanges.CREDIT_EVENTS,
                        RmqRoutingKeys.CREDIT_CHANGED,
                        new CreditChangedEvent(userId, amount, "ADD", reason, referenceId,
                                Instant.now().toEpochMilli())
                );
                return new WalletResponse(userId, wallet.getBalance());
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException(WalletErrorCode.WALLET_LOCK_TIMEOUT);
        }
    }
}
