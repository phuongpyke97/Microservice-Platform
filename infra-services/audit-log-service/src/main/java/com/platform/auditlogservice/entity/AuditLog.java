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
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "source_ip", length = 50)
    private String sourceIp;

    @Column(length = 50)
    private String status;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(nullable = false)
    private long timestamp;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(Long userId, String action, String sourceIp, String status, String metadataJson, long timestamp) {
        this.userId = userId;
        this.action = action;
        this.sourceIp = sourceIp;
        this.status = status;
        this.metadataJson = metadataJson;
        this.timestamp = timestamp;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getAction() {
        return action;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public String getStatus() {
        return status;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
