package com.dailyatelier.dailyatelier.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
@Slf4j
public class CloudinaryCleanupWorker {
    private static final int MAX_ERROR_LENGTH = 500;

    private final CloudinaryCleanupTransactionService transactionService;
    private final CloudinaryService cloudinaryService;
    private final Clock clock;
    private final CloudinaryCleanupProperties properties;

    public CloudinaryCleanupWorker(
            CloudinaryCleanupTransactionService transactionService,
            CloudinaryService cloudinaryService,
            Clock clock,
            CloudinaryCleanupProperties properties) {
        this.transactionService = transactionService;
        this.cloudinaryService = cloudinaryService;
        this.clock = clock;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "#{@cloudinaryCleanupProperties.intervalMs}",
            initialDelayString = "#{@cloudinaryCleanupProperties.intervalMs}"
    )
    public void processCleanupBatch() {
        for (int processed = 0; processed < properties.getBatchSize(); processed++) {
            Optional<CloudinaryCleanupClaim> claimed =
                    transactionService.claimNext(LocalDateTime.now(clock));
            if (claimed.isEmpty()) {
                return;
            }
            Optional<CloudinaryCleanupClaim> prepared =
                    transactionService.prepareAttempt(
                            claimed.get().cleanupId(),
                            claimed.get().claimToken(),
                            LocalDateTime.now(clock)
                    );
            prepared.ifPresent(this::processClaim);
        }
    }

    private void processClaim(CloudinaryCleanupClaim claim) {
        try {
            cloudinaryService.deleteOriginal(
                    claim.publicId(),
                    claim.resourceType()
            );
            transactionService.markDone(
                    claim.cleanupId(),
                    claim.claimToken(),
                    LocalDateTime.now(clock)
            );
        } catch (CloudinaryDeleteException exception) {
            handleFailure(claim, exception, exception.isRetryable());
        } catch (RuntimeException exception) {
            handleFailure(claim, exception, true);
        }
    }

    private void handleFailure(
            CloudinaryCleanupClaim claim,
            RuntimeException exception,
            boolean retryable) {
        LocalDateTime failedAt = LocalDateTime.now(clock);
        String error = summarize(exception);
        if (!retryable || claim.attemptCount() >= properties.getMaxAttempts()) {
            transactionService.markFailed(
                    claim.cleanupId(),
                    claim.claimToken(),
                    failedAt,
                    error
            );
            log.error(
                    "Cloudinary cleanup permanently failed: cleanupId={}, publicId={}",
                    claim.cleanupId(),
                    claim.publicId(),
                    exception
            );
            return;
        }
        transactionService.scheduleRetry(
                claim.cleanupId(),
                claim.claimToken(),
                failedAt.plus(Duration.ofMillis(backoffMs(claim.attemptCount()))),
                error
        );
        log.warn(
                "Cloudinary cleanup scheduled for retry: cleanupId={}, attempt={}",
                claim.cleanupId(),
                claim.attemptCount(),
                exception
        );
    }

    private long backoffMs(int failedAttempt) {
        long multiplier = 1L << Math.min(failedAttempt - 1, 30);
        if (properties.getInitialBackoffMs()
                > properties.getMaxBackoffMs() / multiplier) {
            return properties.getMaxBackoffMs();
        }
        return Math.min(
                properties.getInitialBackoffMs() * multiplier,
                properties.getMaxBackoffMs()
        );
    }

    private String summarize(RuntimeException exception) {
        String message = exception.getMessage();
        String summary = exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return summary.length() <= MAX_ERROR_LENGTH
                ? summary
                : summary.substring(0, MAX_ERROR_LENGTH);
    }
}
