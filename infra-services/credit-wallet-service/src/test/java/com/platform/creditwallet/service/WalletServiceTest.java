package com.platform.creditwallet.service;

import com.platform.common.core.exception.BaseException;
import com.platform.creditwallet.dto.response.WalletResponse;
import com.platform.creditwallet.entity.Wallet;
import com.platform.creditwallet.exception.WalletErrorCode;
import com.platform.creditwallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private RedissonClient redissonClient;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private RLock rLock;

    private WalletService walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(walletRepository, redissonClient, rabbitTemplate);
    }

    private void lockAcquired() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
    }

    private void lockNotAcquired() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);
    }

    // --- getBalance ---

    @Test
    void getBalance_existing_returnsBalance() {
        Wallet w = new Wallet(1L, 100);
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(w));

        WalletResponse response = walletService.getBalance(1L);

        assertThat(response.balance()).isEqualTo(100);
        assertThat(response.userId()).isEqualTo(1L);
    }

    @Test
    void getBalance_notFound_throws() {
        when(walletRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getBalance(99L))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getErrorCode())
                        .isEqualTo(WalletErrorCode.WALLET_NOT_FOUND));
    }

    // --- deductCredit ---

    @Test
    void deductCredit_valid_reducesBalance() throws InterruptedException {
        lockAcquired();
        Wallet w = new Wallet(1L, 50);
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(w));
        when(walletRepository.save(w)).thenReturn(w);

        WalletResponse response = walletService.deductCredit(1L, 20, "consume", "ref-1");

        assertThat(response.balance()).isEqualTo(30);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        verify(rLock).unlock();
    }

    @Test
    void deductCredit_zeroAmount_throws() {
        assertThatThrownBy(() -> walletService.deductCredit(1L, 0, "r", "ref"))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getErrorCode())
                        .isEqualTo(WalletErrorCode.WALLET_INVALID_AMOUNT));
    }

    @Test
    void deductCredit_lockTimeout_throws() throws InterruptedException {
        lockNotAcquired();

        assertThatThrownBy(() -> walletService.deductCredit(1L, 5, "r", "ref"))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getErrorCode())
                        .isEqualTo(WalletErrorCode.WALLET_LOCK_TIMEOUT));
    }

    @Test
    void deductCredit_insufficientBalance_throws() throws InterruptedException {
        lockAcquired();
        Wallet w = new Wallet(1L, 3);
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(w));

        assertThatThrownBy(() -> walletService.deductCredit(1L, 10, "r", "ref"))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getErrorCode())
                        .isEqualTo(WalletErrorCode.WALLET_INSUFFICIENT_CREDIT));
        verify(rLock).unlock();
    }

    @Test
    void deductCredit_walletNotFound_throws() throws InterruptedException {
        lockAcquired();
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.deductCredit(1L, 5, "r", "ref"))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getErrorCode())
                        .isEqualTo(WalletErrorCode.WALLET_NOT_FOUND));
        verify(rLock).unlock();
    }

    // --- addCredit ---

    @Test
    void addCredit_existingWallet_incrementsBalance() throws InterruptedException {
        lockAcquired();
        Wallet w = new Wallet(2L, 10);
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(w));
        when(walletRepository.save(w)).thenReturn(w);

        WalletResponse response = walletService.addCredit(2L, 5, "top_up", "txn-1");

        assertThat(response.balance()).isEqualTo(15);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        verify(rLock).unlock();
    }

    @Test
    void addCredit_noWallet_createsAndAdds() throws InterruptedException {
        lockAcquired();
        Wallet emptyWallet = new Wallet(3L, 0);
        when(walletRepository.findByUserId(3L)).thenReturn(Optional.empty());
        when(walletRepository.save(any(Wallet.class))).thenReturn(emptyWallet);

        WalletResponse response = walletService.addCredit(3L, 2, "trial", null);

        assertThat(response.balance()).isEqualTo(2);
        verify(rLock).unlock();
    }

    @Test
    void addCredit_zeroAmount_throws() {
        assertThatThrownBy(() -> walletService.addCredit(1L, 0, "r", "ref"))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getErrorCode())
                        .isEqualTo(WalletErrorCode.WALLET_INVALID_AMOUNT));
    }

    @Test
    void addCredit_lockTimeout_throws() throws InterruptedException {
        lockNotAcquired();

        assertThatThrownBy(() -> walletService.addCredit(1L, 5, "r", "ref"))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getErrorCode())
                        .isEqualTo(WalletErrorCode.WALLET_LOCK_TIMEOUT));
    }
}
