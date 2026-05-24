package com.platform.crbtcoreadapter.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ringtone_assignments")
@Getter
@Setter
@NoArgsConstructor
public class RingtoneAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String msisdn;

    @Column(nullable = false, length = 500)
    private String ringtoneUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SyncStatus status;

    @Column(length = 100)
    private String mytoneTransactionId;

    @Column(length = 500)
    private String errorMessage;

    private int retryCount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        status = SyncStatus.PENDING;
        retryCount = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum SyncStatus {
        PENDING, SYNCING, ACTIVE, FAILED, REMOVED
    }

    public RingtoneAssignment(Long userId, String msisdn, String ringtoneUrl) {
        this.userId = userId;
        this.msisdn = msisdn;
        this.ringtoneUrl = ringtoneUrl;
    }
}
