package com.platform.crbtcampaign.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "user_lyria_history")
public class UserLyriaHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String msisdn;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 50)
    private String genre;

    @Column(nullable = false, length = 50)
    private String mood;

    @Column(length = 50)
    private String instrument;

    @Column(name = "audio_url", nullable = false, length = 500)
    private String audioUrl;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds = 45;

    @Column(nullable = false)
    private boolean deleted = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserLyriaHistory() {}

    public UserLyriaHistory(Long userId, String msisdn, String title, String genre, String mood, String instrument, String audioUrl) {
        this.userId = userId;
        this.msisdn = msisdn;
        this.title = title;
        this.genre = genre;
        this.mood = mood;
        this.instrument = instrument;
        this.audioUrl = audioUrl;
        this.durationSeconds = 45;
        this.deleted = false;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getMsisdn() { return msisdn; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getGenre() { return genre; }
    public String getMood() { return mood; }
    public String getInstrument() { return instrument; }
    public String getAudioUrl() { return audioUrl; }
    public int getDurationSeconds() { return durationSeconds; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public Instant getCreatedAt() { return createdAt; }
}
