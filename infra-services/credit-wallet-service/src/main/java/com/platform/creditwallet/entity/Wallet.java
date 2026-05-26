package com.platform.creditwallet.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private int balance;

    @Version
    private Long version;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Wallet() {
    }

    public Wallet(Long userId, int balance) {
        this.userId = userId;
        this.balance = balance;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public int getBalance() { return balance; }

    public void addBalance(int amount) {
        if (amount <= 0) throw new IllegalArgumentException("addBalance: amount must be positive");
        this.balance += amount;
    }

    /**
     * Guard for entity invariant: balance must never go negative.
     * Service layer MUST check balance before calling this; if this throws,
     * it means a concurrency bug bypassed the service-level check.
     */
    public void deductBalance(int amount) {
        if (amount <= 0) throw new IllegalArgumentException("deductBalance: amount must be positive");
        if (this.balance < amount) {
            throw new IllegalStateException(
                "Balance invariant violated: balance=" + this.balance + ", requested=" + amount);
        }
        this.balance -= amount;
    }
}
