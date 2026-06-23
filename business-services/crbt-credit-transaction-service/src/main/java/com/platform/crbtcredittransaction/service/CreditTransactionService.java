package com.platform.crbtcredittransaction.service;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.response.PageResponse;
import com.platform.common.rmq.event.CreditChangedEvent;
import com.platform.crbtcredittransaction.dto.response.CreditTransactionResponse;
import com.platform.crbtcredittransaction.entity.CreditTransaction;
import com.platform.crbtcredittransaction.exception.CreditTransactionErrorCode;
import com.platform.crbtcredittransaction.repository.CreditTransactionRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditTransactionService {

    private final CreditTransactionRepository repository;

    public CreditTransactionService(CreditTransactionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void save(CreditChangedEvent event) {
        CreditTransaction transaction = new CreditTransaction(
            event.userId(),
            event.amount(),
            event.direction(),
            event.reason(),
            event.referenceId(),
            event.timestamp(),
            event.isFree() != null ? event.isFree() : false,
            event.genType() != null ? event.genType() : "OTHER",
            event.beforeBalance(),
            event.afterBalance(),
            event.model()
        );
        repository.save(transaction);
    }

    @Transactional(readOnly = true)
    public PageResponse<CreditTransactionResponse> query(
        Long userId,
        String direction,
        String reason,
        Long fromTs,
        Long toTs,
        Pageable pageable
    ) {
        if (fromTs != null && toTs != null && fromTs > toTs) {
            throw new BaseException(CreditTransactionErrorCode.CREDIT_TRANSACTION_INVALID_DATE_RANGE);
        }

        return PageResponse.from(repository.findAll(filter(userId, direction, reason, fromTs, toTs), pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public String exportCsv(Long userId, String direction, String reason, Long fromTs, Long toTs) {
        if (fromTs != null && toTs != null && fromTs > toTs) {
            throw new BaseException(CreditTransactionErrorCode.CREDIT_TRANSACTION_INVALID_DATE_RANGE);
        }

        List<CreditTransaction> transactions = repository.findAll(filter(userId, direction, reason, fromTs, toTs));

        StringBuilder csv = new StringBuilder();
        csv.append("ID,User ID,Before Balance,After Balance,Amount,Direction,Gen Type,Model,Is Free,Reason,Reference ID,Timestamp,Created At\n");

        for (CreditTransaction tx : transactions) {
            csv.append(tx.getId()).append(",")
               .append(tx.getUserId()).append(",")
               .append(tx.getBeforeBalance() != null ? tx.getBeforeBalance() : "").append(",")
               .append(tx.getAfterBalance() != null ? tx.getAfterBalance() : "").append(",")
               .append(tx.getAmount()).append(",")
               .append(tx.getDirection()).append(",")
               .append(tx.getGenType()).append(",")
               .append(tx.getModel() != null ? tx.getModel() : "").append(",")
               .append(tx.isFree()).append(",")
               .append("\"").append(tx.getReason()).append("\"").append(",")
               .append(tx.getReferenceId()).append(",")
               .append(tx.getTimestamp()).append(",")
               .append(tx.getCreatedAt()).append("\n");
        }

        return csv.toString();
    }

    private Specification<CreditTransaction> filter(
        Long userId,
        String direction,
        String reason,
        Long fromTs,
        Long toTs
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (direction != null) {
                predicates.add(cb.equal(root.get("direction"), direction));
            }
            if (reason != null) {
                predicates.add(cb.like(cb.lower(root.get("reason")), "%" + reason.toLowerCase() + "%"));
            }
            if (fromTs != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), fromTs));
            }
            if (toTs != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), toTs));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private CreditTransactionResponse toResponse(CreditTransaction transaction) {
        return new CreditTransactionResponse(
            transaction.getId(),
            transaction.getUserId(),
            transaction.getAmount(),
            transaction.getDirection(),
            transaction.getReason(),
            transaction.getReferenceId(),
            transaction.getTimestamp(),
            transaction.getCreatedAt(),
            transaction.isFree(),
            transaction.getGenType(),
            transaction.getBeforeBalance(),
            transaction.getAfterBalance(),
            transaction.getModel()
        );
    }
}
