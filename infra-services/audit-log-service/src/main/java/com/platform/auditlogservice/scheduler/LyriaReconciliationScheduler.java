package com.platform.auditlogservice.scheduler;

import com.platform.auditlogservice.service.LyriaReportService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LyriaReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(LyriaReconciliationScheduler.class);
    private final LyriaReportService reportService;

    public LyriaReconciliationScheduler(LyriaReportService reportService) {
        this.reportService = reportService;
    }

    // Runs every day at 00:00 AM Asia/Ho_Chi_Minh to aggregate and overwrite the previous day's stats
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void reconcilePreviousDay() {
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1);
        log.info("[LYRIA-RECONCILE] Starting daily reconciliation scheduler for date: {}", yesterday);
        try {
            reportService.reconcileDailyStats(yesterday);
            log.info("[LYRIA-RECONCILE-OK] Reconciled daily stats successfully for date: {}", yesterday);
        } catch (Exception e) {
            log.error("[LYRIA-RECONCILE-FAILED] Failed to reconcile daily stats for date: {}", yesterday, e);
        }
    }
}
