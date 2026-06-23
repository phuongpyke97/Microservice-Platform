package com.platform.creditwallet.service;

import com.platform.common.core.exception.BaseException;
import com.platform.common.rmq.RmqExchanges;
import com.platform.common.rmq.RmqRoutingKeys;
import com.platform.common.rmq.event.CreditChangedEvent;
import com.platform.creditwallet.dto.response.WalletResponse;
import com.platform.creditwallet.entity.Wallet;
import com.platform.creditwallet.exception.WalletErrorCode;
import com.platform.creditwallet.repository.WalletRepository;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);
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

    private RBucket<String> getBalanceBucket(Long userId) {
        return redissonClient.getBucket("wallet:balance:" + userId, StringCodec.INSTANCE);
    }

    private void updateCacheAfterCommit(Long userId, int balance) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    getBalanceBucket(userId).set(String.valueOf(balance), 1, TimeUnit.HOURS);
                    log.info("[WALLET-CACHE-UPDATED] Redis cache updated after DB commit: userId={}, newBalance={}", userId, balance);
                }
            });
        } else {
            getBalanceBucket(userId).set(String.valueOf(balance), 1, TimeUnit.HOURS);
            log.info("[WALLET-CACHE-UPDATED] Redis cache updated (no active tx): userId={}, newBalance={}", userId, balance);
        }
    }

    /**
     * Read balance: cache-first, fallback to DB.
     * readOnly = true → no unnecessary transaction overhead on cache hits.
     */
    @Transactional
    public WalletResponse getBalance(Long userId) {
        RBucket<String> bucket = getBalanceBucket(userId);
        String cachedBalance = bucket.get();
        if (cachedBalance != null) {
            try {
                return new WalletResponse(userId, Integer.parseInt(cachedBalance));
            } catch (NumberFormatException e) {
                // Corrupted cache entry — fall through to DB
            }
        }

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> walletRepository.save(new Wallet(userId, 2)));
        bucket.set(String.valueOf(wallet.getBalance()), 1, TimeUnit.HOURS);
        return new WalletResponse(userId, wallet.getBalance());
    }

    /**
     * Deduct credit.
     * Concurrency: Redisson distributed lock per userId — single lock strategy, no DB-level pessimistic lock.
     * Cache: updated only after DB commit via TransactionSynchronization.
     */
    @Transactional
    public WalletResponse deductCredit(Long userId, int amount, String reason, String referenceId) {
        return deductCredit(userId, amount, reason, referenceId, false, "OTHER", null);
    }

    @Transactional
    public WalletResponse deductCredit(Long userId, int amount, String reason, String referenceId, Boolean isFree, String genType) {
        return deductCredit(userId, amount, reason, referenceId, isFree, genType, null);
    }

    @Transactional
    public WalletResponse deductCredit(Long userId, int amount, String reason, String referenceId, Boolean isFree, String genType, String model) {
        if (amount <= 0) throw new BaseException(WalletErrorCode.WALLET_INVALID_AMOUNT);

        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + userId);
        try {
            boolean acquired = lock.tryLock(LOCK_TIMEOUT_SECONDS, LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) throw new BaseException(WalletErrorCode.WALLET_LOCK_TIMEOUT);

            try {
                Wallet wallet = walletRepository.findByUserId(userId)
                        .orElseGet(() -> walletRepository.save(new Wallet(userId, 2)));

                if (wallet.getBalance() < amount) {
                    throw new BaseException(WalletErrorCode.WALLET_INSUFFICIENT_CREDIT);
                }
                int beforeBalance = wallet.getBalance();
                wallet.deductBalance(amount);
                walletRepository.save(wallet);
                int afterBalance = wallet.getBalance();

                updateCacheAfterCommit(userId, wallet.getBalance());

                rabbitTemplate.convertAndSend(
                        RmqExchanges.CREDIT_EVENTS,
                        RmqRoutingKeys.CREDIT_DEDUCTED,
                        new CreditChangedEvent(userId, amount, "DEDUCT", reason, referenceId,
                                Instant.now().toEpochMilli(), isFree, genType, beforeBalance, afterBalance, model)
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

    /**
     * Add credit (e.g. top-up, refund).
     * Same lock strategy as deductCredit for consistency.
     * Cache: updated only after DB commit via TransactionSynchronization.
     */
    @Transactional
    public WalletResponse addCredit(Long userId, int amount, String reason, String referenceId) {
        return addCredit(userId, amount, reason, referenceId, false, "OTHER", null);
    }

    @Transactional
    public WalletResponse addCredit(Long userId, int amount, String reason, String referenceId, Boolean isFree, String genType) {
        return addCredit(userId, amount, reason, referenceId, isFree, genType, null);
    }

    @Transactional
    public WalletResponse addCredit(Long userId, int amount, String reason, String referenceId, Boolean isFree, String genType, String model) {
        if (amount <= 0) throw new BaseException(WalletErrorCode.WALLET_INVALID_AMOUNT);

        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + userId);
        try {
            boolean acquired = lock.tryLock(LOCK_TIMEOUT_SECONDS, LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) throw new BaseException(WalletErrorCode.WALLET_LOCK_TIMEOUT);

            try {
                Wallet wallet = walletRepository.findByUserId(userId)
                        .orElseGet(() -> walletRepository.save(new Wallet(userId, 2)));
                int beforeBalance = wallet.getBalance();
                wallet.addBalance(amount);
                walletRepository.save(wallet);
                int afterBalance = wallet.getBalance();

                updateCacheAfterCommit(userId, wallet.getBalance());

                rabbitTemplate.convertAndSend(
                        RmqExchanges.CREDIT_EVENTS,
                        RmqRoutingKeys.CREDIT_CHANGED,
                        new CreditChangedEvent(userId, amount, "ADD", reason, referenceId,
                                Instant.now().toEpochMilli(), isFree, genType, beforeBalance, afterBalance, model)
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
