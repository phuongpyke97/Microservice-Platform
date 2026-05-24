package com.platform.audiogeneration.repository;

import com.platform.audiogeneration.entity.AudioJob;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AudioJobRepository extends JpaRepository<AudioJob, Long> {
    List<AudioJob> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT COUNT(j) FROM AudioJob j WHERE j.userId = :userId AND j.status IN ('PENDING', 'PROCESSING')")
    long countActiveJobsByUserId(Long userId);
}
