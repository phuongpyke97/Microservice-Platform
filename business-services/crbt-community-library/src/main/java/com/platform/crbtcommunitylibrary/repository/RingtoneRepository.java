package com.platform.crbtcommunitylibrary.repository;

import com.platform.crbtcommunitylibrary.entity.Ringtone;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RingtoneRepository extends JpaRepository<Ringtone, Long>, JpaSpecificationExecutor<Ringtone> {

    @Query(value = "SELECT r.* FROM ringtones r JOIN categories c ON r.category_id = c.id WHERE r.deleted = false AND r.status = true AND LOWER(c.name) = LOWER(:genre) ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<Ringtone> findRandomByGenre(@Param("genre") String genre);

    @Query(value = "SELECT * FROM ringtones WHERE deleted = false AND status = true ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<Ringtone> findRandom();

    long countByDeletedFalse();

    long countByDeletedFalseAndStatusTrue();

    long countByDeletedFalseAndStatusFalse();

    @Query("SELECT COALESCE(SUM(r.selectionCount), 0) FROM Ringtone r WHERE r.deleted = false")
    long sumSelectionCountByDeletedFalse();

    long countByCategoryIdAndDeletedFalse(Long categoryId);

    long countByMoodIdAndDeletedFalse(Long moodId);

    Optional<Ringtone> findByAudioUrlAndDeletedFalse(String audioUrl);

    @Query("SELECT r FROM Ringtone r WHERE r.deleted = false AND r.audioUrl LIKE %:filename")
    List<Ringtone> findByAudioUrlContainingAndDeletedFalse(@Param("filename") String filename);
}
