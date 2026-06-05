package com.platform.auditlogservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "lyria_request_logs")
public class LyriaRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 50)
    private String msisdn;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "candidate_tokens", nullable = false)
    private int candidateTokens;

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LyriaRequestLog() {
    }

    public LyriaRequestLog(Long userId, String msisdn, String model, int promptTokens, 
                           int candidateTokens, int totalTokens, int latencyMs, 
                           String status, String errorMessage) {
        this.userId = userId;
        this.msisdn = msisdn;
        this.model = model;
        this.promptTokens = promptTokens;
        this.candidateTokens = candidateTokens;
        this.totalTokens = totalTokens;
        this.latencyMs = latencyMs;
        this.status = status;
        this.errorMessage = errorMessage;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public String getModel() {
        return model;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCandidateTokens() {
        return candidateTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
