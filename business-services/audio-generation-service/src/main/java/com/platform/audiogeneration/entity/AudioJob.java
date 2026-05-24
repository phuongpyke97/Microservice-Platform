package com.platform.audiogeneration.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "audio_jobs")
@Getter
@Setter
@NoArgsConstructor
public class AudioJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 500)
    private String prompt;

    @Column(length = 50)
    private String voiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    private String resultUrl;

    @Column(length = 500)
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        status = JobStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum JobStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }

    public AudioJob(Long userId, String prompt, String voiceId) {
        this.userId = userId;
        this.prompt = prompt;
        this.voiceId = voiceId;
    }
}
