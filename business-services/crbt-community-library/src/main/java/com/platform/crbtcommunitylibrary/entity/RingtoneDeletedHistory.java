package com.platform.crbtcommunitylibrary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "ringtone_deleted_history")
public class RingtoneDeletedHistory {

    @Id
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(name = "artist_name", nullable = false, length = 100)
    private String artistName;

    @Column(name = "category_name", nullable = false, length = 50)
    private String categoryName;

    @Column(name = "selection_count", nullable = false)
    private long selectionCount;

    @CreationTimestamp
    @Column(name = "deleted_at", nullable = false, updatable = false)
    private Instant deletedAt;

    protected RingtoneDeletedHistory() {
    }

    public RingtoneDeletedHistory(Long id, String title, String artistName, String categoryName, long selectionCount) {
        this.id = id;
        this.title = title;
        this.artistName = artistName;
        this.categoryName = categoryName;
        this.selectionCount = selectionCount;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getArtistName() {
        return artistName;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public long getSelectionCount() {
        return selectionCount;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
