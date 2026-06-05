package com.platform.auditlogservice.repository;

import com.platform.auditlogservice.entity.LyriaRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface LyriaRequestLogRepository extends JpaRepository<LyriaRequestLog, Long>, JpaSpecificationExecutor<LyriaRequestLog> {
}
