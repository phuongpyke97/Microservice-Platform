package com.platform.auditlogservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.auditlogservice.dto.response.AuditLogResponse;
import com.platform.auditlogservice.entity.AuditLog;
import com.platform.auditlogservice.entity.LyriaDailyStat;
import com.platform.auditlogservice.entity.LyriaRequestLog;
import com.platform.auditlogservice.exception.AuditErrorCode;
import com.platform.auditlogservice.repository.AuditLogRepository;
import com.platform.auditlogservice.repository.LyriaDailyStatRepository;
import com.platform.auditlogservice.repository.LyriaRequestLogRepository;
import com.platform.common.core.exception.BaseException;
import com.platform.common.core.response.PageResponse;
import com.platform.common.rmq.RmqExchanges;
import com.platform.common.rmq.RmqRoutingKeys;
import com.platform.common.rmq.event.AuditLogEvent;
import com.platform.common.rmq.event.LyriaCostAlertEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
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
public class AuditLogService {

    private final AuditLogRepository repository;
    private final LyriaRequestLogRepository requestLogRepository;
    private final LyriaDailyStatRepository dailyStatRepository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    @Value("${lyria.alert.email:admin@platform.com}")
    private String alertEmail;

    @Value("${lyria.alert.threshold-usd:100.0}")
    private double alertThresholdUsd;

    public AuditLogService(AuditLogRepository repository,
                           LyriaRequestLogRepository requestLogRepository,
                           LyriaDailyStatRepository dailyStatRepository,
                           ObjectMapper objectMapper,
                           RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.requestLogRepository = requestLogRepository;
        this.dailyStatRepository = dailyStatRepository;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public void save(AuditLogEvent event) {
        repository.save(new AuditLog(
                event.userId(),
                event.action(),
                event.sourceIp(),
                event.status(),
                event.metadataJson(),
                event.timestamp()
        ));

        // Process Lyria token stats and request details
        if (event.action() != null && event.action().contains("/campaigns/generate") && event.metadataJson() != null) {
            try {
                JsonNode rootNode = objectMapper.readTree(event.metadataJson());
                JsonNode tokenUsage = rootNode.path("lyria_token_usage");
                
                int durationMs = rootNode.path("duration_ms").asInt(0);
                LocalDate statDate = LocalDate.ofInstant(
                    Instant.ofEpochMilli(event.timestamp()), 
                    ZoneId.of("Asia/Ho_Chi_Minh")
                );

                if ("SUCCESS".equalsIgnoreCase(event.status()) && !tokenUsage.isMissingNode()) {
                    int promptTokens = tokenUsage.path("prompt_tokens").asInt();
                    int candidateTokens = tokenUsage.path("candidate_tokens").asInt();
                    int totalTokens = tokenUsage.path("total_tokens").asInt();
                    String msisdn = tokenUsage.path("msisdn").asText("");
                    String model = tokenUsage.path("model").asText("lyria-3-clip-preview");

                    // 1. Insert detailed request log
                    requestLogRepository.save(new LyriaRequestLog(
                        event.userId(),
                        msisdn,
                        model,
                        promptTokens,
                        candidateTokens,
                        totalTokens,
                        durationMs,
                        "SUCCESS",
                        null
                    ));

                    // 2. Upsert daily stats
                    LyriaDailyStat stat = dailyStatRepository.findByStatDate(statDate)
                        .orElseGet(() -> new LyriaDailyStat(statDate));
                    
                    int oldTotalSuccess = stat.getTotalRequests();
                    stat.setTotalRequests(oldTotalSuccess + 1);
                    stat.setTotalPromptTokens(stat.getTotalPromptTokens() + promptTokens);
                    stat.setTotalCandidateTokens(stat.getTotalCandidateTokens() + candidateTokens);
                    stat.setTotalTokens(stat.getTotalTokens() + totalTokens);
                    
                    long newTotalLatency = ((long) oldTotalSuccess * stat.getAvgLatencyMs()) + durationMs;
                    stat.setAvgLatencyMs((int) (newTotalLatency / stat.getTotalRequests()));
                    
                    BigDecimal addedCost = BigDecimal.valueOf(totalTokens).multiply(new BigDecimal("0.000001"));
                    stat.setEstimatedCostUsd(stat.getEstimatedCostUsd().add(addedCost));

                    BigDecimal threshold = BigDecimal.valueOf(alertThresholdUsd);
                    if (stat.getEstimatedCostUsd().compareTo(threshold) >= 0 && !stat.isAlertSent()) {
                        stat.setAlertSent(true);
                        try {
                            rabbitTemplate.convertAndSend(
                                RmqExchanges.AUDIT_EVENTS,
                                RmqRoutingKeys.LYRIA_COST_ALERT,
                                new LyriaCostAlertEvent(alertEmail, threshold, stat.getEstimatedCostUsd(), statDate)
                            );
                            org.slf4j.LoggerFactory.getLogger(AuditLogService.class)
                                .warn("[LYRIA-COST-ALERT] Daily cost {} crossed threshold {}. Alert event published to {}", 
                                      stat.getEstimatedCostUsd(), threshold, alertEmail);
                        } catch (Exception mqEx) {
                            org.slf4j.LoggerFactory.getLogger(AuditLogService.class)
                                .error("[LYRIA-COST-ALERT-FAILED] Failed to publish cost alert event", mqEx);
                        }
                    }
                    
                    dailyStatRepository.save(stat);
                } else if ("FAILED".equalsIgnoreCase(event.status())) {
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

                    // 1. Insert detailed request log (FAILED)
                    requestLogRepository.save(new LyriaRequestLog(
                        event.userId(),
                        msisdn,
                        "lyria-3-clip-preview",
                        0,
                        0,
                        0,
                        durationMs,
                        "FAILED",
                        errorMsg
                    ));

                    // 2. Increment failed requests in daily stats
                    LyriaDailyStat stat = dailyStatRepository.findByStatDate(statDate)
                        .orElseGet(() -> new LyriaDailyStat(statDate));
                    stat.setFailedRequests(stat.getFailedRequests() + 1);
                    dailyStatRepository.save(stat);
                }
            } catch (Exception e) {
                // Log and swallow exception to keep the audit listener reliable
                org.slf4j.LoggerFactory.getLogger(AuditLogService.class)
                    .error("Failed to parse Lyria token usage in AuditLogService: {}", e.getMessage(), e);
            }
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> query(Long userId, String action, String status,
                                                Long fromTs, Long toTs, Pageable pageable) {
        if (fromTs != null && toTs != null && fromTs > toTs) {
            throw new BaseException(AuditErrorCode.AUDIT_INVALID_DATE_RANGE);
        }
        Page<AuditLogResponse> page = repository.findAll(filter(userId, action, status, fromTs, toTs), pageable)
                .map(this::toResponse);
        return PageResponse.from(page);
    }

    private Specification<AuditLog> filter(Long userId, String action, String status, Long fromTs, Long toTs) {
        return (root, queryObj, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (fromTs != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), fromTs));
            }
            if (toTs != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), toTs));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getUserId(),
                log.getAction(),
                log.getSourceIp(),
                log.getStatus(),
                log.getMetadataJson(),
                log.getTimestamp(),
                log.getCreatedAt()
        );
    }
}
