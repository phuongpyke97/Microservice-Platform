package com.platform.auditlogservice.repository;

import com.platform.auditlogservice.entity.LyriaDailyStat;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LyriaDailyStatRepository extends JpaRepository<LyriaDailyStat, LocalDate> {
    Optional<LyriaDailyStat> findByStatDate(LocalDate statDate);
    List<LyriaDailyStat> findByStatDateBetweenOrderByStatDateAsc(LocalDate startDate, LocalDate endDate);
}
