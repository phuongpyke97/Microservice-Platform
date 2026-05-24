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
        this.balance += amount;
    }

    public void deductBalance(int amount) {
        if (this.balance < amount) {
            throw new IllegalArgumentException("Insufficient credit");
        }
        this.balance -= amount;
    }
}
