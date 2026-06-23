package com.platform.crbtcredittransaction.repository;

import com.platform.crbtcredittransaction.entity.CreditTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long>, JpaSpecificationExecutor<CreditTransaction> {

    @Query("SELECT t.userId, t.direction, SUM(t.amount) FROM CreditTransaction t " +
           "WHERE t.userId IN :userIds GROUP BY t.userId, t.direction")
    List<Object[]> getStatsByUserIds(@Param("userIds") List<Long> userIds);

    @Query("SELECT " +
           "COALESCE(SUM(CASE WHEN t.direction = 'ADD' THEN t.amount ELSE 0 END), 0), " +
           "COALESCE(SUM(CASE WHEN t.direction = 'DEDUCT' THEN t.amount ELSE 0 END), 0) " +
           "FROM CreditTransaction t WHERE t.userId IN :userIds")
    List<Object[]> sumStatsByUserIds(@Param("userIds") List<Long> userIds);

    @Query("SELECT t.direction, COALESCE(SUM(t.amount), 0) " +
           "FROM CreditTransaction t " +
           "WHERE t.userId = :userId " +
           "AND (:direction IS NULL OR t.direction = :direction) " +
           "AND (:reason IS NULL OR t.reason = :reason) " +
           "AND (:fromTs IS NULL OR t.timestamp >= :fromTs) " +
           "AND (:toTs IS NULL OR t.timestamp <= :toTs) " +
           "GROUP BY t.direction")
    List<Object[]> sumTransactionsByFilters(
            @Param("userId") Long userId,
            @Param("direction") String direction,
            @Param("reason") String reason,
            @Param("fromTs") Long fromTs,
            @Param("toTs") Long toTs);
}

