package com.platform.crbtcampaign.repository;

import com.platform.crbtcampaign.entity.UserLyriaHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserLyriaHistoryRepository extends JpaRepository<UserLyriaHistory, Long>, JpaSpecificationExecutor<UserLyriaHistory> {
    List<UserLyriaHistory> findByUserIdAndDeletedFalseOrderByCreatedAtDesc(Long userId);

    /**
     * Count how many times this user has already generated a track with the exact same
     * prompt (genre + mood + instrument). Used to append a _V2/_V3... suffix to the title
     * so the user can distinguish repeated generations of an identical prompt.
     * Counts every row (including soft-deleted) so the version index keeps increasing.
     */
    @Query("SELECT COUNT(h) FROM UserLyriaHistory h WHERE h.userId = :userId "
            + "AND h.genre = :genre AND h.mood = :mood "
            + "AND ((:instrument IS NULL AND h.instrument IS NULL) OR h.instrument = :instrument)")
    long countSamePromptGenerations(Long userId, String genre, String mood, String instrument);
    List<UserLyriaHistory> findByIdGreaterThanAndDeletedFalseAndCreatedAtBeforeOrderByIdAsc(
            Long lastId, 
            java.time.Instant cutoffTime, 
            org.springframework.data.domain.Pageable pageable
    );
}
