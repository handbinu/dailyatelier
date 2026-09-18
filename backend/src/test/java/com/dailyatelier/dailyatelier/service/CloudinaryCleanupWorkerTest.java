package com.dailyatelier.dailyatelier.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudinaryCleanupWorkerTest {
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 9, 18, 20, 0);

    @Mock
    private CloudinaryCleanupTransactionService transactionService;

    @Mock
    private CloudinaryService cloudinaryService;

    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(
                Instant.parse("2026-09-18T11:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
    }

    @Test
    void marksSuccessfulDeletionDone() {
        CloudinaryCleanupClaim claim = claim(1);
        stubClaim(claim);

        worker(3, 1_000L, 8_000L).processCleanupBatch();

        verify(cloudinaryService).deleteOriginal("arts/member/work", "image");
        verify(transactionService).markDone(1L, "claim-token", NOW);
    }

    @Test
    void schedulesTransientFailureWithExponentialBackoff() {
        CloudinaryCleanupClaim claim = claim(3);
        stubClaim(claim);
        doThrow(new CloudinaryDeleteException("temporary", true))
                .when(cloudinaryService)
                .deleteOriginal("arts/member/work", "image");

        worker(5, 1_000L, 8_000L).processCleanupBatch();

        verify(transactionService).scheduleRetry(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("claim-token"),
                org.mockito.ArgumentMatchers.eq(NOW.plusSeconds(4)),
                contains("temporary")
        );
        verify(transactionService, never())
                .markFailed(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString()
                );
    }

    @Test
    void marksFailureWhenMaximumAttemptsIsReached() {
        CloudinaryCleanupClaim claim = claim(3);
        stubClaim(claim);
        doThrow(new IllegalStateException("still failing"))
                .when(cloudinaryService)
                .deleteOriginal("arts/member/work", "image");

        worker(3, 1_000L, 8_000L).processCleanupBatch();

        verify(transactionService).markFailed(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("claim-token"),
                org.mockito.ArgumentMatchers.eq(NOW),
                contains("still failing")
        );
        verify(transactionService, never())
                .scheduleRetry(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString()
                );
    }

    @Test
    void marksPermanentFailureImmediately() {
        CloudinaryCleanupClaim claim = claim(1);
        stubClaim(claim);
        doThrow(new CloudinaryDeleteException("bad request", false))
                .when(cloudinaryService)
                .deleteOriginal("arts/member/work", "image");

        worker(5, 1_000L, 8_000L).processCleanupBatch();

        verify(transactionService).markFailed(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("claim-token"),
                org.mockito.ArgumentMatchers.eq(NOW),
                contains("bad request")
        );
        verify(transactionService, never()).scheduleRetry(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void performsNoExternalCallWhenNoWorkIsClaimed() {
        when(transactionService.claimNext(NOW)).thenReturn(Optional.empty());

        worker(3, 1_000L, 8_000L).processCleanupBatch();

        verify(cloudinaryService, never()).deleteOriginal(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    private CloudinaryCleanupWorker worker(
            int maxAttempts,
            long initialBackoffMs,
            long maxBackoffMs) {
        CloudinaryCleanupProperties properties = new CloudinaryCleanupProperties(
                60_000L,
                1,
                maxAttempts,
                initialBackoffMs,
                maxBackoffMs,
                120_000L,
                5_000,
                30_000
        );
        return new CloudinaryCleanupWorker(
                transactionService,
                cloudinaryService,
                clock,
                properties
        );
    }

    private CloudinaryCleanupClaim claim(int attemptCount) {
        return new CloudinaryCleanupClaim(
                1L,
                "arts/member/work",
                "image",
                "claim-token",
                attemptCount
        );
    }

    private void stubClaim(CloudinaryCleanupClaim claim) {
        when(transactionService.claimNext(NOW))
                .thenReturn(Optional.of(claim));
        when(transactionService.prepareAttempt(1L, "claim-token", NOW))
                .thenReturn(Optional.of(claim));
    }
}
