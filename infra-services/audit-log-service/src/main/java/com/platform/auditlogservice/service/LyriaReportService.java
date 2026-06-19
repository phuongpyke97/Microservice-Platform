package com.platform.auditlogservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.auditlogservice.dto.response.LyriaDailyStatResponse;
import com.platform.auditlogservice.dto.response.LyriaRequestLogResponse;
import com.platform.auditlogservice.dto.response.LyriaSummaryResponse;
import com.platform.auditlogservice.entity.AuditLog;
import com.platform.auditlogservice.entity.LyriaDailyStat;
import com.platform.auditlogservice.entity.LyriaRequestLog;
import com.platform.auditlogservice.repository.AuditLogRepository;
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
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public LyriaReportService(LyriaRequestLogRepository requestLogRepository,
                              LyriaDailyStatRepository dailyStatRepository,
                              AuditLogRepository auditLogRepository,
                              ObjectMapper objectMapper) {
        this.requestLogRepository = requestLogRepository;
        this.dailyStatRepository = dailyStatRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public LyriaSummaryResponse getSummary(LocalDate startDate, LocalDate endDate) {
        List<LyriaDailyStat> stats = dailyStatRepository.findByStatDateBetweenOrderByStatDateAsc(startDate, endDate);
        long totalTokens = 0;
        int totalSongs = 0;
        BigDecimal totalCostUsd = BigDecimal.ZERO;
        for (LyriaDailyStat stat : stats) {
            totalTokens += stat.getTotalTokens();
            totalSongs += stat.getTotalRequests();
            if (stat.getEstimatedCostUsd() != null) {
                totalCostUsd = totalCostUsd.add(stat.getEstimatedCostUsd());
            }
        }
        double avgTokens = totalSongs > 0 ? (double) totalTokens / totalSongs : 0.0;
        return new LyriaSummaryResponse(totalTokens, totalSongs, Math.round(avgTokens * 10.0) / 10.0, totalCostUsd);
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
        Instant startInstant = date.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        Instant endInstant = date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();

        long startTs = startInstant.toEpochMilli();
        long endTs = endInstant.toEpochMilli();

        // 1. Fetch raw logs from audit_logs table
        List<AuditLog> rawLogs = auditLogRepository.findByActionContainingAndTimestampGreaterThanEqualAndTimestampLessThan(
            "/campaigns/generate", startTs, endTs
        );

        // 2. Clear existing lyria_request_logs for this date range to prevent duplicates
        requestLogRepository.deleteLogsInDateRange(startInstant, endInstant);

        int totalRequests = 0;
        int failedRequests = 0;
        long totalPromptTokens = 0;
        long totalCandidateTokens = 0;
        long totalTokens = 0;
        long totalLatencyMs = 0;

        List<LyriaRequestLog> logsToSave = new ArrayList<>();

        for (AuditLog auditLog : rawLogs) {
            try {
                if (auditLog.getMetadataJson() == null) continue;

                JsonNode rootNode = objectMapper.readTree(auditLog.getMetadataJson());
                JsonNode tokenUsage = rootNode.path("lyria_token_usage");

                int durationMs = rootNode.path("duration_ms").asInt(0);

                if ("SUCCESS".equalsIgnoreCase(auditLog.getStatus()) && !tokenUsage.isMissingNode()) {
                    int promptTokens = tokenUsage.path("prompt_tokens").asInt(0);
                    int candidateTokens = tokenUsage.path("candidate_tokens").asInt(0);
                    int totalTokensVal = tokenUsage.path("total_tokens").asInt(0);
                    String msisdn = tokenUsage.path("msisdn").asText("");
                    String model = tokenUsage.path("model").asText("lyria-3-clip-preview");

                    totalRequests++;
                    totalPromptTokens += promptTokens;
                    totalCandidateTokens += candidateTokens;
                    totalTokens += totalTokensVal;
                    totalLatencyMs += durationMs;

                    LyriaRequestLog logEntity = new LyriaRequestLog(
                        auditLog.getUserId(),
                        msisdn,
                        model,
                        promptTokens,
                        candidateTokens,
                        totalTokensVal,
                        durationMs,
                        "SUCCESS",
                        null
                    );
                    logEntity.setCreatedAt(Instant.ofEpochMilli(auditLog.getTimestamp()));
                    logsToSave.add(logEntity);
                } else if ("FAILED".equalsIgnoreCase(auditLog.getStatus())) {
                    failedRequests++;

                    String exceptionClass = rootNode.path("exception").asText("");
                    String message = rootNode.path("message").asText("");
                    String errorMsg = exceptionClass.substring(exceptionClass.lastIndexOf('.') + 1) + ": " + message;
                    if (errorMsg.length() > 500) {
                        errorMsg = errorMsg.substring(0, 497) + "...";
                    }

                    String msisdn = "";
                    if (!tokenUsage.isMissingNode() && tokenUsage.has("msisdn")) {
                        msisdn = tokenUsage.path("msisdn").asText();
                    }

                    LyriaRequestLog logEntity = new LyriaRequestLog(
                        auditLog.getUserId(),
                        msisdn,
                        "lyria-3-clip-preview",
                        0,
                        0,
                        0,
                        durationMs,
                        "FAILED",
                        errorMsg
                    );
                    logEntity.setCreatedAt(Instant.ofEpochMilli(auditLog.getTimestamp()));
                    logsToSave.add(logEntity);
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(LyriaReportService.class)
                    .error("Failed to parse audit log metadata in reconciliation for log id {}: {}", auditLog.getId(), e.getMessage(), e);
            }
        }

        // Save new logs
        if (!logsToSave.isEmpty()) {
            requestLogRepository.saveAll(logsToSave);
        }

        // 3. Upsert/Update the aggregated daily stats
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
