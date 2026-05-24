package com.platform.auth.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Set;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "msisdn"),
        @UniqueConstraint(columnNames = "email")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20)
    private String msisdn;

    @Column(length = 120)
    private String email;

    @Column(name = "password_hash", length = 72)
    private String passwordHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    private Set<String> roles;

    @Column(name = "credit_balance", nullable = false)
    private int creditBalance = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected User() {
    }

    public User(String msisdn, String email, String passwordHash, Set<String> roles, int creditBalance) {
        this.msisdn = msisdn;
        this.email = email;
        this.passwordHash = passwordHash;
        this.roles = roles;
        this.creditBalance = creditBalance;
        this.status = UserStatus.ACTIVE;
    }

    public Long getId() { return id; }
    public String getMsisdn() { return msisdn; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Set<String> getRoles() { return roles; }
    public int getCreditBalance() { return creditBalance; }
    public UserStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setEmail(String email) { this.email = email; }
    public void setStatus(UserStatus status) { this.status = status; }
    public void setCreditBalance(int creditBalance) { this.creditBalance = creditBalance; }
}
