package com.platform.crbtcredittransaction.repository;

import com.platform.crbtcredittransaction.entity.CreditTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long>, JpaSpecificationExecutor<CreditTransaction> {
}
