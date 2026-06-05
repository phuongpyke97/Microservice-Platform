package com.platform.auditlogservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "lyria_daily_stats")
public class LyriaDailyStat {

    @Id
    @Column(name = "stat_date")
    private LocalDate statDate;

    @Column(name = "total_requests", nullable = false)
    private int totalRequests = 0;

    @Column(name = "failed_requests", nullable = false)
    private int failedRequests = 0;

    @Column(name = "total_prompt_tokens", nullable = false)
    private long totalPromptTokens = 0;

    @Column(name = "total_candidate_tokens", nullable = false)
    private long totalCandidateTokens = 0;

    @Column(name = "total_tokens", nullable = false)
    private long totalTokens = 0;

    @Column(name = "avg_latency_ms", nullable = false)
    private int avgLatencyMs = 0;

    @Column(name = "estimated_cost_usd", nullable = false, precision = 12, scale = 4)
    private BigDecimal estimatedCostUsd = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected LyriaDailyStat() {
    }

    public LyriaDailyStat(LocalDate statDate) {
        this.statDate = statDate;
    }

    public LocalDate getStatDate() {
        return statDate;
    }

    public void setStatDate(LocalDate statDate) {
        this.statDate = statDate;
    }

    public int getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(int totalRequests) {
        this.totalRequests = totalRequests;
    }

    public int getFailedRequests() {
        return failedRequests;
    }

    public void setFailedRequests(int failedRequests) {
        this.failedRequests = failedRequests;
    }

    public long getTotalPromptTokens() {
        return totalPromptTokens;
    }

    public void setTotalPromptTokens(long totalPromptTokens) {
        this.totalPromptTokens = totalPromptTokens;
    }

    public long getTotalCandidateTokens() {
        return totalCandidateTokens;
    }

    public void setTotalCandidateTokens(long totalCandidateTokens) {
        this.totalCandidateTokens = totalCandidateTokens;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(long totalTokens) {
        this.totalTokens = totalTokens;
    }

    public int getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public void setAvgLatencyMs(int avgLatencyMs) {
        this.avgLatencyMs = avgLatencyMs;
    }

    public BigDecimal getEstimatedCostUsd() {
        return estimatedCostUsd;
    }

    public void setEstimatedCostUsd(BigDecimal estimatedCostUsd) {
        this.estimatedCostUsd = estimatedCostUsd;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
