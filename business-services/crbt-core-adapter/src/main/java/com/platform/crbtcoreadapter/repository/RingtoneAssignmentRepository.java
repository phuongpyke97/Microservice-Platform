package com.platform.crbtcoreadapter.repository;

import com.platform.crbtcoreadapter.entity.RingtoneAssignment;
import com.platform.crbtcoreadapter.entity.RingtoneAssignment.SyncStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RingtoneAssignmentRepository extends JpaRepository<RingtoneAssignment, Long> {
    List<RingtoneAssignment> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<RingtoneAssignment> findByMsisdnAndStatus(String msisdn, SyncStatus status);
    List<RingtoneAssignment> findByStatus(SyncStatus status);
}
