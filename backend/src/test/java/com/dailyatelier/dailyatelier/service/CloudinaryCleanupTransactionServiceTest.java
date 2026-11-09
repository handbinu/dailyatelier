package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.CloudinaryCleanup;
import com.dailyatelier.dailyatelier.entity.CloudinaryCleanupStatus;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.repository.CloudinaryCleanupRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cloudinary-cleanup-transaction;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        CloudinaryCleanupTransactionService.class,
        CloudinaryCleanupWorker.class,
        CloudinaryCleanupTransactionServiceTest.FixedClockConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CloudinaryCleanupTransactionServiceTest {
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 9, 18, 20, 0);

    @Autowired
    private CloudinaryCleanupTransactionService transactionService;

    @Autowired
    private CloudinaryCleanupRepository cleanupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CloudinaryCleanupWorker worker;

    @MockitoBean
    private CloudinaryService cloudinaryService;

    @BeforeEach
    void cleanUp() {
        cleanupRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void claimsDueWorkAndCompletesIdempotently() {
        CloudinaryCleanup cleanup = savePending("arts/member/done", null);

        CloudinaryCleanupClaim claim = transactionService.claimNext(NOW)
                .orElseThrow();
        assertThat(claim.cleanupId()).isEqualTo(cleanup.getCleanupId());
        assertThat(reload(cleanup).getStatus())
                .isEqualTo(CloudinaryCleanupStatus.PROCESSING);
        assertThat(claim.attemptCount()).isZero();
        CloudinaryCleanupClaim prepared = transactionService.prepareAttempt(
                cleanup.getCleanupId(), claim.claimToken(), NOW
        ).orElseThrow();
        assertThat(prepared.attemptCount()).isEqualTo(1);

        transactionService.markDone(cleanup.getCleanupId(), claim.claimToken(), NOW);
        transactionService.markDone(cleanup.getCleanupId(), claim.claimToken(), NOW.plusSeconds(1));

        CloudinaryCleanup completed = reload(cleanup);
        assertThat(completed.getStatus()).isEqualTo(CloudinaryCleanupStatus.DONE);
        assertThat(completed.getCompletedAt()).isEqualTo(NOW);
        assertThat(transactionService.claimNext(NOW.plusDays(1))).isEmpty();
    }

    @Test
    void retriesOnlyAtScheduledTimeAndRecordsAttempt() {
        CloudinaryCleanup cleanup = savePending("arts/member/retry", null);
        CloudinaryCleanupClaim claim = transactionService.claimNext(NOW).orElseThrow();
        transactionService.prepareAttempt(
                cleanup.getCleanupId(), claim.claimToken(), NOW
        ).orElseThrow();

        LocalDateTime retryAt = NOW.plusMinutes(2);
        transactionService.scheduleRetry(
                cleanup.getCleanupId(),
                claim.claimToken(),
                retryAt,
                "temporary"
        );

        CloudinaryCleanup pending = reload(cleanup);
        assertThat(pending.getStatus()).isEqualTo(CloudinaryCleanupStatus.PENDING);
        assertThat(pending.getAttemptCount()).isEqualTo(1);
        assertThat(pending.getNextAttemptAt()).isEqualTo(retryAt);
        assertThat(pending.getLastError()).isEqualTo("temporary");
        assertThat(transactionService.claimNext(retryAt.minusNanos(1))).isEmpty();
        CloudinaryCleanupClaim retryClaim = transactionService.claimNext(retryAt).orElseThrow();
        CloudinaryCleanupClaim preparedRetry = transactionService.prepareAttempt(
                cleanup.getCleanupId(), retryClaim.claimToken(), retryAt
        ).orElseThrow();
        assertThat(preparedRetry.attemptCount()).isEqualTo(2);
    }

    @Test
    void recordsFinalFailureAndAttemptCount() {
        CloudinaryCleanup cleanup = savePending("arts/member/failed", null);
        CloudinaryCleanupClaim claim = transactionService.claimNext(NOW).orElseThrow();
        transactionService.prepareAttempt(
                cleanup.getCleanupId(), claim.claimToken(), NOW
        ).orElseThrow();

        transactionService.markFailed(
                cleanup.getCleanupId(),
                claim.claimToken(),
                NOW,
                "permanent"
        );

        CloudinaryCleanup failed = reload(cleanup);
        assertThat(failed.getStatus()).isEqualTo(CloudinaryCleanupStatus.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(1);
        assertThat(failed.getCompletedAt()).isEqualTo(NOW);
        assertThat(failed.getLastError()).isEqualTo("permanent");
    }

    @Test
    void reclaimsExpiredLeaseAfterCrash() {
        CloudinaryCleanup cleanup = savePending("arts/member/crashed", null);

        CloudinaryCleanupClaim first = transactionService.claimNext(NOW).orElseThrow();
        assertThat(transactionService.claimNext(NOW.plusSeconds(119))).isEmpty();

        CloudinaryCleanupClaim reclaimed = transactionService
                .claimNext(NOW.plusSeconds(120))
                .orElseThrow();

        assertThat(reclaimed.cleanupId()).isEqualTo(cleanup.getCleanupId());
        assertThat(reclaimed.claimToken()).isNotEqualTo(first.claimToken());
        assertThat(reclaimed.attemptCount()).isZero();
    }

    @Test
    void ignoresLateResultFromExpiredClaim() {
        CloudinaryCleanup cleanup = savePending("arts/member/fenced", null);
        CloudinaryCleanupClaim first = transactionService.claimNext(NOW).orElseThrow();
        CloudinaryCleanupClaim second = transactionService
                .claimNext(NOW.plusSeconds(120))
                .orElseThrow();

        transactionService.markDone(
                cleanup.getCleanupId(),
                first.claimToken(),
                NOW.plusSeconds(121)
        );

        CloudinaryCleanup stillOwned = reload(cleanup);
        assertThat(stillOwned.getStatus()).isEqualTo(CloudinaryCleanupStatus.PROCESSING);
        assertThat(stillOwned.getProcessingToken()).isEqualTo(second.claimToken());
    }

    @Test
    void refusesToDeleteCurrentlyReferencedProfileImage() {
        User user = new User();
        user.setUserId("referenced-user");
        user.setPassword("password");
        user.setName("name");
        user.setNickname("nickname");
        user.setPhoneNumber("01012345678");
        user.setEmail("ref@example.com");
        user.setJoinDate(NOW);
        user.setUserStatus(0);
        user.setProfileImagePublicId("profiles/referenced-user/photo");
        userRepository.saveAndFlush(user);
        CloudinaryCleanup cleanup = savePending(
                "profiles/referenced-user/photo",
                null
        );

        assertThat(transactionService.claimNext(NOW)).isEmpty();

        CloudinaryCleanup rejected = reload(cleanup);
        assertThat(rejected.getStatus()).isEqualTo(CloudinaryCleanupStatus.FAILED);
        assertThat(rejected.getAttemptCount()).isZero();
        assertThat(rejected.getLastError()).contains("currently referenced");
    }

    @Test
    void rechecksReferencesImmediatelyBeforeExternalAttempt() {
        CloudinaryCleanup cleanup = savePending(
                "profiles/late-reference/photo",
                null
        );
        CloudinaryCleanupClaim claim = transactionService.claimNext(NOW).orElseThrow();

        User user = new User();
        user.setUserId("late-reference");
        user.setPassword("password");
        user.setName("name");
        user.setNickname("late-ref");
        user.setPhoneNumber("01012345678");
        user.setEmail("late@example.com");
        user.setJoinDate(NOW);
        user.setUserStatus(0);
        user.setProfileImagePublicId("profiles/late-reference/photo");
        userRepository.saveAndFlush(user);

        assertThat(transactionService.prepareAttempt(
                cleanup.getCleanupId(), claim.claimToken(), NOW
        )).isEmpty();
        assertThat(reload(cleanup).getStatus()).isEqualTo(CloudinaryCleanupStatus.FAILED);
        assertThat(reload(cleanup).getAttemptCount()).isZero();
    }

    @Test
    void concurrentWorkersClaimOneCleanupOnlyOnce() throws Exception {
        CloudinaryCleanup cleanup = savePending("arts/member/concurrent", null);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<CloudinaryCleanupClaim>> first = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return transactionService.claimNext(NOW);
            });
            Future<Optional<CloudinaryCleanupClaim>> second = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return transactionService.claimNext(NOW);
            });
            start.countDown();

            List<Optional<CloudinaryCleanupClaim>> results = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );
            assertThat(results).filteredOn(Optional::isPresent).hasSize(1);
            assertThat(results).filteredOn(Optional::isEmpty).hasSize(1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(reload(cleanup).getStatus())
                .isEqualTo(CloudinaryCleanupStatus.PROCESSING);
    }

    @Test
    void callsCloudinaryOutsideDatabaseTransaction() {
        CloudinaryCleanup cleanup = savePending("arts/member/external", null);
        AtomicBoolean transactionActive = new AtomicBoolean(true);
        doAnswer(invocation -> {
            transactionActive.set(
                    TransactionSynchronizationManager.isActualTransactionActive()
            );
            return null;
        }).when(cloudinaryService).deleteOriginal(
                "arts/member/external",
                "image"
        );

        worker.processCleanupBatch();

        assertThat(transactionActive).isFalse();
        assertThat(reload(cleanup).getStatus())
                .isEqualTo(CloudinaryCleanupStatus.DONE);
    }

    private CloudinaryCleanup savePending(
            String publicId,
            LocalDateTime nextAttemptAt) {
        return cleanupRepository.saveAndFlush(
                CloudinaryCleanup.pending(publicId, "image", nextAttemptAt)
        );
    }

    private CloudinaryCleanup reload(CloudinaryCleanup cleanup) {
        return cleanupRepository.findById(cleanup.getCleanupId()).orElseThrow();
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(
                    Instant.parse("2026-09-18T11:00:00Z"),
                    ZoneId.of("Asia/Seoul")
            );
        }

        @Bean
        CloudinaryCleanupProperties cloudinaryCleanupProperties() {
            return new CloudinaryCleanupProperties(
                    60_000L, 20, 5, 60_000L, 3_600_000L,
                    120_000L, 5_000, 30_000
            );
        }
    }
}
