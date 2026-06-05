package com.platform.auditlogservice.service;

import com.platform.auditlogservice.dto.response.LyriaDailyStatResponse;
import com.platform.auditlogservice.dto.response.LyriaRequestLogResponse;
import com.platform.auditlogservice.dto.response.LyriaSummaryResponse;
import com.platform.auditlogservice.entity.LyriaDailyStat;
import com.platform.auditlogservice.entity.LyriaRequestLog;
import com.platform.auditlogservice.repository.LyriaDailyStatRepository;
import com.platform.auditlogservice.repository.LyriaRequestLogRepository;
import com.platform.common.core.response.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LyriaReportService {

    private final LyriaRequestLogRepository requestLogRepository;
    private final LyriaDailyStatRepository dailyStatRepository;

    public LyriaReportService(LyriaRequestLogRepository requestLogRepository,
                              LyriaDailyStatRepository dailyStatRepository) {
        this.requestLogRepository = requestLogRepository;
        this.dailyStatRepository = dailyStatRepository;
    }

    @Transactional(readOnly = true)
    public LyriaSummaryResponse getSummary(LocalDate startDate, LocalDate endDate) {
        List<LyriaDailyStat> stats = dailyStatRepository.findByStatDateBetweenOrderByStatDateAsc(startDate, endDate);
        long totalTokens = 0;
        int totalSongs = 0;
        for (LyriaDailyStat stat : stats) {
            totalTokens += stat.getTotalTokens();
            totalSongs += stat.getTotalRequests();
        }
        double avgTokens = totalSongs > 0 ? (double) totalTokens / totalSongs : 0.0;
        return new LyriaSummaryResponse(totalTokens, totalSongs, Math.round(avgTokens * 10.0) / 10.0);
    }

    @Transactional(readOnly = true)
    public List<LyriaDailyStatResponse> getDailyStats(LocalDate startDate, LocalDate endDate) {
        List<LyriaDailyStat> stats = dailyStatRepository.findByStatDateBetweenOrderByStatDateAsc(startDate, endDate);
        return stats.stream().map(s -> new LyriaDailyStatResponse(
            s.getStatDate(),
            s.getTotalRequests(),
            s.getFailedRequests(),
            s.getTotalPromptTokens(),
            s.getTotalCandidateTokens(),
            s.getTotalTokens(),
            s.getAvgLatencyMs(),
            s.getEstimatedCostUsd()
        )).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<LyriaRequestLogResponse> getRequestLogs(LocalDate startDate, LocalDate endDate,
                                                                String msisdn, String status, Pageable pageable) {
        Page<LyriaRequestLog> page = requestLogRepository.findAll(filter(startDate, endDate, msisdn, status), pageable);
        Page<LyriaRequestLogResponse> responsePage = page.map(l -> new LyriaRequestLogResponse(
            l.getId(),
            l.getUserId(),
            l.getMsisdn(),
            l.getModel(),
            l.getPromptTokens(),
            l.getCandidateTokens(),
            l.getTotalTokens(),
            l.getLatencyMs(),
            l.getStatus(),
            l.getErrorMessage(),
            l.getCreatedAt()
        ));
        return PageResponse.from(responsePage);
    }

    private Specification<LyriaRequestLog> filter(LocalDate startDate, LocalDate endDate, String msisdn, String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startDate != null) {
                Instant startInstant = startDate.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startInstant));
            }
            if (endDate != null) {
                Instant endInstant = endDate.plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
                predicates.add(cb.lessThan(root.get("createdAt"), endInstant));
            }
            if (msisdn != null && !msisdn.isBlank()) {
                predicates.add(cb.like(root.get("msisdn"), "%" + msisdn.trim() + "%"));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional
    public void reconcileDailyStats(LocalDate date) {
        List<LyriaRequestLog> logs = requestLogRepository.findAll(
            filter(date, date, null, null)
        );

        int totalRequests = 0;
        int failedRequests = 0;
        long totalPromptTokens = 0;
        long totalCandidateTokens = 0;
        long totalTokens = 0;
        long totalLatencyMs = 0;

        for (LyriaRequestLog log : logs) {
            if ("SUCCESS".equalsIgnoreCase(log.getStatus())) {
                totalRequests++;
                totalPromptTokens += log.getPromptTokens();
                totalCandidateTokens += log.getCandidateTokens();
                totalTokens += log.getTotalTokens();
                totalLatencyMs += log.getLatencyMs();
            } else {
                failedRequests++;
            }
        }

        int avgLatencyMs = totalRequests > 0 ? (int) (totalLatencyMs / totalRequests) : 0;
        BigDecimal cost = BigDecimal.valueOf(totalTokens).multiply(new BigDecimal("0.000001"));

        LyriaDailyStat stat = dailyStatRepository.findByStatDate(date)
            .orElseGet(() -> new LyriaDailyStat(date));

        stat.setTotalRequests(totalRequests);
        stat.setFailedRequests(failedRequests);
        stat.setTotalPromptTokens(totalPromptTokens);
        stat.setTotalCandidateTokens(totalCandidateTokens);
        stat.setTotalTokens(totalTokens);
        stat.setAvgLatencyMs(avgLatencyMs);
        stat.setEstimatedCostUsd(cost);

        dailyStatRepository.save(stat);
    }
}
