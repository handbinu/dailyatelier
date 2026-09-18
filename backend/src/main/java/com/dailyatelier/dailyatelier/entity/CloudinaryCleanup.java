package com.dailyatelier.dailyatelier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "cloudinary_cleanup",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_cloudinary_cleanup_active_public_id",
                columnNames = "active_public_id"
        ),
        indexes = @Index(
                name = "idx_cloudinary_cleanup_pending",
                columnList = "status, next_attempt_at, cleanup_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CloudinaryCleanup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cleanupId;

    @Column(nullable = false, length = 320)
    private String publicId;

    @Column(nullable = false, length = 30)
    private String resourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CloudinaryCleanupStatus status;

    @Column(nullable = false)
    private Integer attemptCount = 0;

    private LocalDateTime nextAttemptAt;

    @Column(length = 500)
    private String lastError;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    @Column(length = 320, insertable = false, updatable = false)
    private String activePublicId;

    public static CloudinaryCleanup pending(
            String publicId,
            String resourceType,
            LocalDateTime nextAttemptAt) {
        CloudinaryCleanup cleanup = new CloudinaryCleanup();
        cleanup.publicId = publicId;
        cleanup.resourceType = resourceType;
        cleanup.status = CloudinaryCleanupStatus.PENDING;
        cleanup.nextAttemptAt = nextAttemptAt;
        return cleanup;
    }

    public void markProcessing() {
        status = CloudinaryCleanupStatus.PROCESSING;
    }

    public void scheduleRetry(LocalDateTime retryAt, String error) {
        status = CloudinaryCleanupStatus.PENDING;
        attemptCount++;
        nextAttemptAt = retryAt;
        lastError = error;
    }

    public void markDone(LocalDateTime completedAt) {
        status = CloudinaryCleanupStatus.DONE;
        this.completedAt = completedAt;
        nextAttemptAt = null;
        lastError = null;
    }

    public void markFailed(LocalDateTime completedAt, String error) {
        status = CloudinaryCleanupStatus.FAILED;
        this.completedAt = completedAt;
        nextAttemptAt = null;
        lastError = error;
    }
}
