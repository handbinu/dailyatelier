package com.dailyatelier.dailyatelier.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest(properties = "spring.flyway.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(
        named = "DAILYATELIER_MYSQL_SCHEMA_TEST",
        matches = "true"
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PointLedgerMySqlConcurrencyTest {

    private static final String FIRST_USER = "mysql-lock-test-a";
    private static final String SECOND_USER = "mysql-lock-test-b";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        deleteFixtures();
        insertUser(FIRST_USER);
        insertUser(SECOND_USER);
        insertAccount(FIRST_USER);
        insertAccount(SECOND_USER);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        deleteFixtures();
    }

    @Test
    void usesRepeatableReadIsolationAndSerializesAccountRowUpdates() throws Exception {
        String isolation = transactionTemplate().execute(status ->
                jdbcTemplate.queryForObject(
                        "select @@transaction_isolation",
                        String.class
                ));
        assertThat(isolation).isEqualToIgnoringCase("REPEATABLE-READ");

        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        Future<?> first = executor.submit(() ->
                transactionTemplate().executeWithoutResult(status -> {
                    lockAccount(FIRST_USER);
                    firstLocked.countDown();
                    await(releaseFirst);
                    jdbcTemplate.update("""
                            update point_account
                            set available_balance = available_balance - 100
                            where user_id = ?
                            """, FIRST_USER);
                }));

        assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
        Future<?> second = executor.submit(() ->
                transactionTemplate().executeWithoutResult(status -> {
                    lockAccount(FIRST_USER);
                    jdbcTemplate.update("""
                            update point_account
                            set available_balance = available_balance - 100
                            where user_id = ?
                            """, FIRST_USER);
                }));

        Thread.sleep(200);
        assertThat(second.isDone()).isFalse();
        releaseFirst.countDown();
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);

        assertThat(availableBalance(FIRST_USER)).isEqualTo(800L);
    }

    @Test
    void detectsOppositeAccountLockOrderAsDeadlockWithoutPartialUpdates()
            throws Exception {
        CountDownLatch firstLocksAcquired = new CountDownLatch(2);
        CountDownLatch requestSecondLocks = new CountDownLatch(1);

        Future<?> first = deadlockingTransfer(
                FIRST_USER,
                SECOND_USER,
                firstLocksAcquired,
                requestSecondLocks
        );
        Future<?> second = deadlockingTransfer(
                SECOND_USER,
                FIRST_USER,
                firstLocksAcquired,
                requestSecondLocks
        );

        assertThat(firstLocksAcquired.await(5, TimeUnit.SECONDS)).isTrue();
        requestSecondLocks.countDown();

        int successCount = 0;
        int deadlockCount = 0;
        for (Future<?> result : List.of(first, second)) {
            try {
                result.get(10, TimeUnit.SECONDS);
                successCount++;
            } catch (ExecutionException exception) {
                deadlockCount++;
            }
        }

        assertThat(successCount).isEqualTo(1);
        assertThat(deadlockCount).isEqualTo(1);
        assertThat(availableBalance(FIRST_USER)).isEqualTo(1_000L);
        assertThat(availableBalance(SECOND_USER)).isEqualTo(1_000L);
    }

    private Future<?> deadlockingTransfer(
            String firstUser,
            String secondUser,
            CountDownLatch firstLocksAcquired,
            CountDownLatch requestSecondLocks) {
        return executor.submit(() ->
                transactionTemplate().executeWithoutResult(status -> {
                    lockAccount(firstUser);
                    firstLocksAcquired.countDown();
                    await(requestSecondLocks);
                    lockAccount(secondUser);
                }));
    }

    private void lockAccount(String userId) {
        jdbcTemplate.queryForObject("""
                select available_balance
                from point_account
                where user_id = ?
                for update
                """, Long.class, userId);
    }

    private long availableBalance(String userId) {
        return jdbcTemplate.queryForObject("""
                select available_balance
                from point_account
                where user_id = ?
                """, Long.class, userId);
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private void insertUser(String userId) {
        jdbcTemplate.update("""
                insert into users (
                    user_id, password, name, nickname, phone_number,
                    email, join_date, user_status, reserve, email_agree
                ) values (?, 'password', 'MySQL 검증', ?, '010-0000-0000',
                    ?, current_timestamp, 0, 0, true)
                """, userId, userId.substring(userId.length() - 1),
                userId + "@example.com");
    }

    private void insertAccount(String userId) {
        jdbcTemplate.update("""
                insert into point_account (
                    user_id, available_balance, held_balance, version,
                    created_at, updated_at
                ) values (?, 1000, 0, 0, current_timestamp, current_timestamp)
                """, userId);
    }

    private void deleteFixtures() {
        jdbcTemplate.update(
                "delete from point_account where user_id in (?, ?)",
                FIRST_USER,
                SECOND_USER
        );
        jdbcTemplate.update(
                "delete from users where user_id in (?, ?)",
                FIRST_USER,
                SECOND_USER
        );
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("MySQL 잠금 대기 시간이 초과되었습니다");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MySQL 잠금 검증이 중단되었습니다", exception);
        }
    }
}
