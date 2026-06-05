package com.platform.auditlogservice.repository;

import com.platform.auditlogservice.entity.LyriaRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface LyriaRequestLogRepository extends JpaRepository<LyriaRequestLog, Long>, JpaSpecificationExecutor<LyriaRequestLog> {

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM LyriaRequestLog r WHERE r.createdAt >= :start AND r.createdAt < :end")
    void deleteLogsInDateRange(@org.springframework.data.repository.query.Param("start") java.time.Instant start, 
                               @org.springframework.data.repository.query.Param("end") java.time.Instant end);
}
