package com.platform.auditlogservice.repository;

import com.platform.auditlogservice.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    java.util.List<AuditLog> findByActionContainingAndTimestampGreaterThanEqualAndTimestampLessThan(
            String action, long start, long end
    );
}
