package com.platform.crbtcommunitylibrary.repository;

import com.platform.crbtcommunitylibrary.entity.RingtoneDeletedHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RingtoneDeletedHistoryRepository extends JpaRepository<RingtoneDeletedHistory, Long> {

    @Query("SELECT COALESCE(SUM(h.selectionCount), 0) FROM RingtoneDeletedHistory h")
    long sumSelectionCount();
}
