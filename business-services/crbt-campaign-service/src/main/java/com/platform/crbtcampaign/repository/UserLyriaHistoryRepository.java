package com.platform.crbtcampaign.repository;

import com.platform.crbtcampaign.entity.UserLyriaHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface UserLyriaHistoryRepository extends JpaRepository<UserLyriaHistory, Long>, JpaSpecificationExecutor<UserLyriaHistory> {
    List<UserLyriaHistory> findByUserIdAndDeletedFalseOrderByCreatedAtDesc(Long userId);
    List<UserLyriaHistory> findByIdGreaterThanAndDeletedFalseAndCreatedAtBeforeOrderByIdAsc(
            Long lastId, 
            java.time.Instant cutoffTime, 
            org.springframework.data.domain.Pageable pageable
    );
}
