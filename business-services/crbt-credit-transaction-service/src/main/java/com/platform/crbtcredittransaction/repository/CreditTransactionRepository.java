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
}

