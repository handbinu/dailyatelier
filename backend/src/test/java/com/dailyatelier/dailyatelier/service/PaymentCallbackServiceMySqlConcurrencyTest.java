package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.config.TimeConfig;
import com.dailyatelier.dailyatelier.entity.PaymentCallbackEvent;
import com.dailyatelier.dailyatelier.entity.PaymentCallbackStatus;
import com.dailyatelier.dailyatelier.entity.PaymentProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PaymentCallbackService.class, PaymentCallbackEventCreationService.class,
        PaymentCallbackReceiveObserver.class, PaymentCallbackTransactionService.class, TimeConfig.class})
@EnabledIfEnvironmentVariable(
        named = "DAILYATELIER_MYSQL_SCHEMA_TEST",
        matches = "true"
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentCallbackServiceMySqlConcurrencyTest {
    private static final String EVENT_ID = "mysql-callback-first-receive";

    @Autowired PaymentCallbackService service;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired DataSource dataSource;
    @MockitoSpyBean PaymentCallbackReceiveObserver receiveObserver;

    @BeforeEach
    void setUp() throws Exception {
        verifyDedicatedSchemaTestDatabase();
        deleteFixture();
    }

    @AfterEach
    void tearDown() {
        deleteFixture();
    }

    @Test
    void concurrentFirstReceiveReturnsSameEventAndProcessesOnce() throws Exception {
        List<Future<PaymentCallbackEvent>> results = receiveConcurrently(
                "mysql-order", "same-hash", "mysql-order", "same-hash");

        PaymentCallbackEvent first = results.get(0).get(10, TimeUnit.SECONDS);
        PaymentCallbackEvent second = results.get(1).get(10, TimeUnit.SECONDS);
        assertThat(second.getCallbackEventId()).isEqualTo(first.getCallbackEventId());
        assertThat(callbackCount()).isEqualTo(1L);

        AtomicInteger processCalls = new AtomicInteger();
        service.process(PaymentProvider.INTERNAL, EVENT_ID, event -> processCalls.incrementAndGet());
        service.process(PaymentProvider.INTERNAL, EVENT_ID, event -> processCalls.incrementAndGet());

        assertThat(processCalls).hasValue(1);
        assertThat(jdbcTemplate.queryForObject("""
                select status
                from payment_callback_event
                where callback_event_id = ?
                """, String.class, first.getCallbackEventId()))
                .isEqualTo(PaymentCallbackStatus.PROCESSED.name());
    }

    @Test
    void concurrentFirstReceiveRejectsDifferentPayloadOrPgOrderId() throws Exception {
        List<Future<PaymentCallbackEvent>> payloadResults = receiveConcurrently(
                "mysql-order", "hash-a", "mysql-order", "hash-b");
        assertOneSuccessAndOneConflict(payloadResults);
        assertThat(callbackCount()).isEqualTo(1L);

        deleteFixture();

        List<Future<PaymentCallbackEvent>> orderResults = receiveConcurrently(
                "mysql-order-a", "same-hash", "mysql-order-b", "same-hash");
        assertOneSuccessAndOneConflict(orderResults);
        assertThat(callbackCount()).isEqualTo(1L);
    }

    private List<Future<PaymentCallbackEvent>> receiveConcurrently(
            String firstOrderId, String firstPayloadHash,
            String secondOrderId, String secondPayloadHash) throws Exception {
        CountDownLatch firstLookupsComplete = new CountDownLatch(2);
        CountDownLatch releaseInserts = new CountDownLatch(1);
        AtomicInteger lookupCount = new AtomicInteger();
        doAnswer(invocation -> {
            if (lookupCount.getAndIncrement() < 2) {
                firstLookupsComplete.countDown();
                if (!releaseInserts.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("최초 콜백 조회 동기화 시간이 초과되었습니다");
                }
            }
            return null;
        }).when(receiveObserver).afterFirstLookupMiss();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<PaymentCallbackEvent>> results = List.of(
                    executor.submit(() -> service.receive(
                            PaymentProvider.INTERNAL, EVENT_ID, firstOrderId, firstPayloadHash)),
                    executor.submit(() -> service.receive(
                            PaymentProvider.INTERNAL, EVENT_ID, secondOrderId, secondPayloadHash))
            );
            assertThat(firstLookupsComplete.await(5, TimeUnit.SECONDS)).isTrue();
            releaseInserts.countDown();
            return results;
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertOneSuccessAndOneConflict(List<Future<PaymentCallbackEvent>> results) throws Exception {
        int successful = 0;
        int conflicts = 0;
        for (Future<PaymentCallbackEvent> result : results) {
            try {
                assertThat(result.get(10, TimeUnit.SECONDS)).isNotNull();
                successful++;
            } catch (ExecutionException exception) {
                assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class)
                        .hasMessage("같은 콜백 이벤트 ID의 원문이 일치하지 않습니다");
                conflicts++;
            }
        }
        assertThat(successful).isEqualTo(1);
        assertThat(conflicts).isEqualTo(1);
    }

    private long callbackCount() {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from payment_callback_event
                where provider = ? and provider_event_id = ?
                """, Long.class, PaymentProvider.INTERNAL.name(), EVENT_ID);
    }

    private void verifyDedicatedSchemaTestDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL())
                    .matches("jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):.*");
            assertThat(connection.getCatalog()).isEqualTo("dailyatelier_schema_test");
        }
    }

    private void deleteFixture() {
        jdbcTemplate.update("""
                delete from payment_callback_event
                where provider = ? and provider_event_id = ?
                """, PaymentProvider.INTERNAL.name(), EVENT_ID);
    }
}
