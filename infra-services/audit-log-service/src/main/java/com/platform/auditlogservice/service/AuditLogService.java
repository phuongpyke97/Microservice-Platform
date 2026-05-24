package com.platform.auditlogservice.service;

import com.platform.auditlogservice.dto.response.AuditLogResponse;
import com.platform.auditlogservice.entity.AuditLog;
import com.platform.auditlogservice.exception.AuditErrorCode;
import com.platform.auditlogservice.repository.AuditLogRepository;
import com.platform.common.core.exception.BaseException;
import com.platform.common.core.response.PageResponse;
import com.platform.common.rmq.event.AuditLogEvent;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void save(AuditLogEvent event) {
        repository.save(new AuditLog(
                event.userId(),
                event.action(),
                event.sourceIp(),
                event.status(),
                event.metadataJson(),
                event.timestamp()
        ));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> query(Long userId, String action, String status,
                                                Long fromTs, Long toTs, Pageable pageable) {
        if (fromTs != null && toTs != null && fromTs > toTs) {
            throw new BaseException(AuditErrorCode.AUDIT_INVALID_DATE_RANGE);
        }
        Page<AuditLogResponse> page = repository.findAll(filter(userId, action, status, fromTs, toTs), pageable)
                .map(this::toResponse);
        return PageResponse.from(page);
    }

    private Specification<AuditLog> filter(Long userId, String action, String status, Long fromTs, Long toTs) {
        return (root, queryObj, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
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

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getUserId(),
                log.getAction(),
                log.getSourceIp(),
                log.getStatus(),
                log.getMetadataJson(),
                log.getTimestamp(),
                log.getCreatedAt()
        );
    }
}
