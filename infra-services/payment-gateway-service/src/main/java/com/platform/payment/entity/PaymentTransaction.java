package com.platform.payment.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "payment_transactions",
        uniqueConstraints = @UniqueConstraint(columnNames = "idempotency_key"))
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "msisdn", nullable = false, length = 20)
    private String msisdn;

    @Column(name = "package_code", nullable = false, length = 50)
    private String packageCode;

    @Column(name = "amount_mmk", nullable = false)
    private long amountMmk;

    @Column(name = "credit_amount", nullable = false)
    private int creditAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "mps_ref", length = 100)
    private String mpsRef;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected PaymentTransaction() {}

    public PaymentTransaction(String idempotencyKey, Long userId, String msisdn, String packageCode,
                              long amountMmk, int creditAmount) {
        this.idempotencyKey = idempotencyKey;
        this.userId = userId;
        this.msisdn = msisdn;
        this.packageCode = packageCode;
        this.amountMmk = amountMmk;
        this.creditAmount = creditAmount;
        this.status = PaymentStatus.PENDING;
    }

    public Long getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Long getUserId() { return userId; }
    public String getMsisdn() { return msisdn; }
    public String getPackageCode() { return packageCode; }
    public long getAmountMmk() { return amountMmk; }
    public int getCreditAmount() { return creditAmount; }
    public PaymentStatus getStatus() { return status; }
    public String getMpsRef() { return mpsRef; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }

    public void markSuccess(String mpsRef) {
        this.status = PaymentStatus.SUCCESS;
        this.mpsRef = mpsRef;
        this.errorMessage = null;
    }

    public void markFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.errorMessage = reason;
    }
}
