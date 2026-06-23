package com.platform.creditwallet.repository;

import com.platform.creditwallet.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(Long userId);

    @Query("SELECT COALESCE(SUM(w.balance), 0) FROM Wallet w WHERE w.userId IN :userIds")
    int sumBalancesByUserIds(@Param("userIds") List<Long> userIds);
}
