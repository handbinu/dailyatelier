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
        indexes = {
                @Index(
                        name = "idx_cloudinary_cleanup_pending",
                        columnList = "status, next_attempt_at, cleanup_id"
                ),
                @Index(
                        name = "idx_cloudinary_cleanup_processing",
                        columnList = "status, processing_deadline, cleanup_id"
                )
        }
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

    @Column(length = 36)
    private String processingToken;

    private LocalDateTime processingDeadline;

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

    public void claim(String token, LocalDateTime deadline) {
        if (status != CloudinaryCleanupStatus.PENDING
                && status != CloudinaryCleanupStatus.PROCESSING) {
            throw new IllegalStateException("Cloudinary cleanup is not claimable");
        }
        status = CloudinaryCleanupStatus.PROCESSING;
        processingToken = token;
        processingDeadline = deadline;
        nextAttemptAt = null;
    }

    public void recordAttempt() {
        requireStatus(CloudinaryCleanupStatus.PROCESSING);
        attemptCount++;
    }

    public void scheduleRetry(LocalDateTime retryAt, String error) {
        requireStatus(CloudinaryCleanupStatus.PROCESSING);
        status = CloudinaryCleanupStatus.PENDING;
        clearClaim();
        nextAttemptAt = retryAt;
        lastError = error;
    }

    public void markDone(LocalDateTime completedAt) {
        requireStatus(CloudinaryCleanupStatus.PROCESSING);
        status = CloudinaryCleanupStatus.DONE;
        clearClaim();
        this.completedAt = completedAt;
        nextAttemptAt = null;
        lastError = null;
    }

    public void markFailed(LocalDateTime completedAt, String error) {
        requireStatus(CloudinaryCleanupStatus.PROCESSING);
        status = CloudinaryCleanupStatus.FAILED;
        clearClaim();
        this.completedAt = completedAt;
        nextAttemptAt = null;
        lastError = error;
    }

    public void rejectReferenced(LocalDateTime completedAt, String error) {
        if (status != CloudinaryCleanupStatus.PENDING
                && status != CloudinaryCleanupStatus.PROCESSING) {
            throw new IllegalStateException("Cloudinary cleanup is not active");
        }
        status = CloudinaryCleanupStatus.FAILED;
        clearClaim();
        this.completedAt = completedAt;
        nextAttemptAt = null;
        lastError = error;
    }

    public boolean isOwnedBy(String token) {
        return status == CloudinaryCleanupStatus.PROCESSING
                && token != null
                && token.equals(processingToken);
    }

    private void clearClaim() {
        processingToken = null;
        processingDeadline = null;
    }

    private void requireStatus(CloudinaryCleanupStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Cloudinary cleanup status must be " + expected
            );
        }
    }
}
