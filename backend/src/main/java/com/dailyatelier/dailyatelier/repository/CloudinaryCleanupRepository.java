package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.CloudinaryCleanup;
import com.dailyatelier.dailyatelier.entity.CloudinaryCleanupStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface CloudinaryCleanupRepository
        extends JpaRepository<CloudinaryCleanup, Long> {
    boolean existsByPublicIdAndStatusIn(
            String publicId,
            Collection<CloudinaryCleanupStatus> statuses);

    @Query(value = """
            SELECT *
            FROM cloudinary_cleanup
            WHERE (
                    status = 'PENDING'
                    AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
                  )
               OR (
                    status = 'PROCESSING'
                    AND processing_deadline <= :now
                  )
            ORDER BY
                CASE WHEN status = 'PROCESSING' THEN 0 ELSE 1 END,
                COALESCE(processing_deadline, next_attempt_at),
                cleanup_id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<CloudinaryCleanup> findNextProcessableForUpdate(
            @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cleanup from CloudinaryCleanup cleanup where cleanup.cleanupId = :cleanupId")
    Optional<CloudinaryCleanup> findByIdForUpdate(
            @Param("cleanupId") Long cleanupId);
}
