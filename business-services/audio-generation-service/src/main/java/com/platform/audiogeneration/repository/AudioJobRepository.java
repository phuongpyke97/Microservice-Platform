package com.platform.audiogeneration.repository;

import com.platform.audiogeneration.entity.AudioJob;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AudioJobRepository extends JpaRepository<AudioJob, Long>, JpaSpecificationExecutor<AudioJob> {
    List<AudioJob> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<AudioJob> findByUserIdAndDeletedFalseOrderByCreatedAtDesc(Long userId);


    @Query("SELECT COUNT(j) FROM AudioJob j WHERE j.userId = :userId AND j.status IN ('PENDING', 'PROCESSING')")
    long countActiveJobsByUserId(Long userId);

    /**
     * Count how many jobs this user has already submitted with the exact same prompt for the
     * given job type. Used to append a _V2/_V3... suffix to the title so re-generated tones
     * with an identical prompt are distinguishable in the user's library.
     */
    long countByUserIdAndJobTypeAndPrompt(Long userId, String jobType, String prompt);

    List<AudioJob> findByIdGreaterThanAndCreatedAtBeforeAndDeletedFalseOrderByIdAsc(Long id, java.time.Instant dateTime, org.springframework.data.domain.Pageable pageable);
}
