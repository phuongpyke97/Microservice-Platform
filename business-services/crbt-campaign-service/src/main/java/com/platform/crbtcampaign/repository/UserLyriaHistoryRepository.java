package com.platform.crbtcampaign.repository;

import com.platform.crbtcampaign.entity.UserLyriaHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserLyriaHistoryRepository extends JpaRepository<UserLyriaHistory, Long> {
    List<UserLyriaHistory> findByUserIdAndDeletedFalseOrderByCreatedAtDesc(Long userId);
}
