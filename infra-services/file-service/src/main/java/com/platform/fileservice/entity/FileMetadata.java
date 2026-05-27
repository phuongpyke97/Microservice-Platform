package com.platform.fileservice.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "file_metadata")
public class FileMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "stored_key", nullable = false, unique = true, length = 255)
    private String storedKey;

    @Column(nullable = false, length = 64)
    private String bucket;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FileStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected FileMetadata() {}

    public FileMetadata(Long userId, String originalName, String storedKey, String bucket,
                        String contentType, long sizeBytes, FileStatus status) {
        this.userId = userId;
        this.originalName = originalName;
        this.storedKey = storedKey;
        this.bucket = bucket;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.status = status;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getOriginalName() { return originalName; }
    public String getStoredKey() { return storedKey; }
    public String getBucket() { return bucket; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public FileStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(FileStatus status) { this.status = status; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public void setStoredKey(String storedKey) { this.storedKey = storedKey; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
}

