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

    @Column(name = "is_free", nullable = false)
    private boolean isFree;

    @Column(name = "gen_type", nullable = false, length = 50)
    private String genType;

    @Column(name = "before_balance")
    private Integer beforeBalance;

    @Column(name = "after_balance")
    private Integer afterBalance;

    @Column(name = "model", length = 60)
    private String model;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CreditTransaction() {
    }

    public CreditTransaction(Long userId, int amount, String direction, String reason, String referenceId, long timestamp,
                             boolean isFree, String genType, Integer beforeBalance, Integer afterBalance, String model) {
        this.userId = userId;
        this.amount = amount;
        this.direction = direction;
        this.reason = reason;
        this.referenceId = referenceId;
        this.timestamp = timestamp;
        this.isFree = isFree;
        this.genType = genType;
        this.beforeBalance = beforeBalance;
        this.afterBalance = afterBalance;
        this.model = model;
    }

    public CreditTransaction(Long userId, int amount, String direction, String reason, String referenceId, long timestamp, boolean isFree, String genType) {
        this(userId, amount, direction, reason, referenceId, timestamp, isFree, genType, null, null, null);
    }

    public CreditTransaction(Long userId, int amount, String direction, String reason, String referenceId, long timestamp) {
        this(userId, amount, direction, reason, referenceId, timestamp, false, "OTHER", null, null, null);
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

    public boolean isFree() {
        return isFree;
    }

    public String getGenType() {
        return genType;
    }

    public Integer getBeforeBalance() {
        return beforeBalance;
    }

    public Integer getAfterBalance() {
        return afterBalance;
    }

    public String getModel() {
        return model;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
