package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.config.TimeConfig;
import com.dailyatelier.dailyatelier.entity.PaymentProvider;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.exception.PointApiException;
import com.dailyatelier.dailyatelier.payment.InternalPointPaymentProvider;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PointChargeService.class, PointAccountService.class,
        InternalPointPaymentProvider.class, DemoPointChargePolicy.class, TimeConfig.class})
@EnabledIfEnvironmentVariable(
        named = "DAILYATELIER_MYSQL_SCHEMA_TEST",
        matches = "true"
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PointChargeServiceMySqlConcurrencyTest {

    private static final String USER_ID = "mysql-charge-create";
    private static final String IDEMPOTENCY_KEY = "mysql-charge-create-key";

    @Autowired
    private PointChargeService service;
    @Autowired
    private PointAccountService pointAccountService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DataSource dataSource;

    private boolean fixtureInitialized;

    @BeforeEach
    void setUp() throws Exception {
        verifyDedicatedSchemaTestDatabase();
        deleteFixture();

        User user = new User();
        user.setUserId(USER_ID);
        user.setPassword("password");
        user.setName("MySQL 충전 생성 사용자");
        user.setNickname("mysql충전생성");
        user.setPhoneNumber("010-0000-0000");
        user.setEmail("mysql-charge@example.com");
        user.setJoinDate(LocalDateTime.of(2026, 10, 20, 12, 0));
        user.setUserStatus(0);
        userRepository.saveAndFlush(user);
        pointAccountService.initializeAccount(USER_ID);
        fixtureInitialized = true;
    }

    @AfterEach
    void tearDown() {
        if (fixtureInitialized) {
            deleteFixture();
        }
    }

    @Test
    void concurrentFirstCreateReturnsSameChargeAndStoresOneRow() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Long>> results = List.of(
                    executor.submit(() -> create(ready, start)),
                    executor.submit(() -> create(ready, start))
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Long firstChargeId = results.get(0).get(10, TimeUnit.SECONDS);
            Long secondChargeId = results.get(1).get(10, TimeUnit.SECONDS);
            assertThat(secondChargeId).isEqualTo(firstChargeId);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from point_charge
                where user_id = ? and idempotency_key = ?
                """, Long.class, USER_ID, IDEMPOTENCY_KEY)).isEqualTo(1L);
    }

    @Test
    void reusedKeyWithDifferentAmountOrProviderKeepsConflictContract() {
        service.create(USER_ID, PaymentProvider.INTERNAL, 10_000, IDEMPOTENCY_KEY);

        assertIdempotencyConflict(() ->
                service.create(USER_ID, PaymentProvider.INTERNAL, 30_000, IDEMPOTENCY_KEY));
        assertIdempotencyConflict(() ->
                service.create(USER_ID, PaymentProvider.NAVER_PAY, 10_000, IDEMPOTENCY_KEY));

        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from point_charge
                where user_id = ? and idempotency_key = ?
                """, Long.class, USER_ID, IDEMPOTENCY_KEY)).isEqualTo(1L);
    }

    private Long create(CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("MySQL 충전 생성 시작 대기 시간이 초과되었습니다");
            }
            return service.create(USER_ID, PaymentProvider.INTERNAL, 10_000, IDEMPOTENCY_KEY)
                    .getChargeId();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void assertIdempotencyConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(PointApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
                    assertThat(exception).hasMessage("멱등성 키가 다른 충전 요청에 이미 사용되었습니다");
                });
    }

    private void verifyDedicatedSchemaTestDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL())
                    .matches("jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):.*");
            assertThat(connection.getCatalog()).isEqualTo("dailyatelier_schema_test");
        }
    }

    private void deleteFixture() {
        jdbcTemplate.update("delete from point_charge where user_id = ?", USER_ID);
        jdbcTemplate.update("delete from point_account where user_id = ?", USER_ID);
        jdbcTemplate.update("delete from users where user_id = ?", USER_ID);
    }
}
