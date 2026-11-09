package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.CloudinaryCleanup;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.CloudinaryCleanupRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CloudinaryCleanupTransactionService {
    private final CloudinaryCleanupRepository cleanupRepository;
    private final ArtRepository artRepository;
    private final UserRepository userRepository;
    private final CloudinaryCleanupProperties properties;

    @Transactional
    public Optional<CloudinaryCleanupClaim> claimNext(LocalDateTime now) {
        Optional<CloudinaryCleanup> candidate;
        while ((candidate = cleanupRepository.findNextProcessableForUpdate(now)).isPresent()) {
            CloudinaryCleanup cleanup = candidate.get();
            if (isReferenced(cleanup.getPublicId())) {
                cleanup.rejectReferenced(
                        now,
                        "Cleanup blocked because the asset is currently referenced"
                );
                cleanupRepository.flush();
                continue;
            }
            String claimToken = UUID.randomUUID().toString();
            cleanup.claim(
                    claimToken,
                    now.plus(Duration.ofMillis(properties.getLeaseMs()))
            );
            return Optional.of(new CloudinaryCleanupClaim(
                    cleanup.getCleanupId(),
                    cleanup.getPublicId(),
                    cleanup.getResourceType(),
                    claimToken,
                    cleanup.getAttemptCount()
            ));
        }
        return Optional.empty();
    }

    @Transactional
    public Optional<CloudinaryCleanupClaim> prepareAttempt(
            Long cleanupId,
            String claimToken,
            LocalDateTime now) {
        Optional<CloudinaryCleanup> owned = cleanupRepository
                .findByIdForUpdate(cleanupId)
                .filter(cleanup -> cleanup.isOwnedBy(claimToken));
        if (owned.isEmpty()) {
            return Optional.empty();
        }
        CloudinaryCleanup cleanup = owned.get();
        if (isReferenced(cleanup.getPublicId())) {
            cleanup.rejectReferenced(
                    now,
                    "Cleanup blocked because the asset is currently referenced"
            );
            return Optional.empty();
        }
        cleanup.recordAttempt();
        return Optional.of(new CloudinaryCleanupClaim(
                cleanup.getCleanupId(),
                cleanup.getPublicId(),
                cleanup.getResourceType(),
                claimToken,
                cleanup.getAttemptCount()
        ));
    }

    @Transactional
    public void markDone(
            Long cleanupId,
            String claimToken,
            LocalDateTime completedAt) {
        cleanupRepository.findByIdForUpdate(cleanupId)
                .filter(cleanup -> cleanup.isOwnedBy(claimToken))
                .ifPresent(cleanup -> cleanup.markDone(completedAt));
    }

    @Transactional
    public void scheduleRetry(
            Long cleanupId,
            String claimToken,
            LocalDateTime retryAt,
            String error) {
        cleanupRepository.findByIdForUpdate(cleanupId)
                .filter(cleanup -> cleanup.isOwnedBy(claimToken))
                .ifPresent(cleanup -> cleanup.scheduleRetry(retryAt, error));
    }

    @Transactional
    public void markFailed(
            Long cleanupId,
            String claimToken,
            LocalDateTime completedAt,
            String error) {
        cleanupRepository.findByIdForUpdate(cleanupId)
                .filter(cleanup -> cleanup.isOwnedBy(claimToken))
                .ifPresent(cleanup -> cleanup.markFailed(completedAt, error));
    }

    private boolean isReferenced(String publicId) {
        return artRepository.existsByCloudinaryPublicId(publicId)
                || userRepository.existsByProfileImagePublicId(publicId);
    }
}
