package com.platform.crbtcredittransaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "credit_transactions")
public class CreditTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, length = 20)
    private String direction;

    @Column(nullable = false, length = 100)
    private String reason;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(nullable = false)
    private long timestamp;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CreditTransaction() {
    }

    public CreditTransaction(Long userId, int amount, String direction, String reason, String referenceId, long timestamp) {
        this.userId = userId;
        this.amount = amount;
        this.direction = direction;
        this.reason = reason;
        this.referenceId = referenceId;
        this.timestamp = timestamp;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public int getAmount() {
        return amount;
    }

    public String getDirection() {
        return direction;
    }

    public String getReason() {
        return reason;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
