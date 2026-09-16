package com.dailyatelier.dailyatelier.loadtest;

import com.zaxxer.hikari.HikariDataSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dailyatelier.dailyatelier.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.hibernate.Session;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate"
        }
)
@EnabledIfEnvironmentVariable(named = "DAILYATELIER_SESSION_TIMEOUT_TEST", matches = "true")
@Import(SessionLockTimeoutMySqlTest.DiagnosticConfiguration.class)
class SessionLockTimeoutMySqlTest {

    private static final Pattern ALLOWED_DATABASE = Pattern.compile(
            "dailyatelier_load_test(?:_[a-z0-9_]+)?"
    );
    private static final String MARKER = "dailyatelier-load-test-v1";
    private static final int TEST_TIMEOUT_SECONDS = 2;
    private static final int PRODUCTION_TIMEOUT_SECONDS = 3;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void verifiesSessionTimeoutAndRestorationForBothLocksAndAllExitPaths() throws Exception {
        assertLoadTestDatabase();
        long globalBefore = queryLong(newConnection(), "select @@global.innodb_lock_wait_timeout");
        long artId = fixtureId("select art_id from art where name = 'LT-HOT-001'");
        String bidderId = "load_bid_001";
        String token = jwtTokenProvider.generateToken(bidderId, 0);

        DiagnosticResponse success = invoke(token, "art", "success", artId, bidderId);
        assertRestored(success, HttpStatus.OK, "SUCCESS");

        DiagnosticResponse domainFailure = invoke(
                token, "point_account", "domain_failure", artId, bidderId
        );
        assertRestored(domainFailure, HttpStatus.UNPROCESSABLE_ENTITY, "DOMAIN_FAILURE");

        DiagnosticResponse artTimeout;
        try (Connection blocker = newConnection()) {
            blocker.setAutoCommit(false);
            lock(blocker, "select art_id from art where art_id = ? for update", artId);
            artTimeout = invoke(token, "art", "timeout", artId, bidderId);
        }
        assertTimeout(artTimeout, "art");

        DiagnosticResponse accountTimeout;
        try (Connection blocker = newConnection()) {
            blocker.setAutoCommit(false);
            lock(
                    blocker,
                    "select user_id from point_account where user_id = ? for update",
                    bidderId
            );
            accountTimeout = invoke(token, "point_account", "timeout", artId, bidderId);
        }
        assertTimeout(accountTimeout, "point_account");

        long globalAfter = queryLong(newConnection(), "select @@global.innodb_lock_wait_timeout");
        assertThat(globalAfter).isEqualTo(globalBefore);

        System.out.printf(
                Locale.ROOT,
                "SESSION_TIMEOUT_DIAGNOSTIC timeoutSeconds=%d globalBefore=%d globalAfter=%d "
                        + "art=%s pointAccount=%s success=%s domainFailure=%s%n",
                TEST_TIMEOUT_SECONDS,
                globalBefore,
                globalAfter,
                artTimeout,
                accountTimeout,
                success,
                domainFailure
        );
    }

    @Test
    void verifiesProductionBidTimeoutRollbackRecoveryAndSessionRestoration() throws Exception {
        assertLoadTestDatabase();
        long globalBefore = queryLong(newConnection(), "select @@global.innodb_lock_wait_timeout");
        long artId = fixtureId("select art_id from art where name = 'LT-HOT-001'");
        String bidderId = "load_bid_001";
        String token = jwtTokenProvider.generateToken(bidderId, 0);
        int bidPrice = nextBidPrice(artId);
        BidState initial = snapshot(artId, bidderId);

        TimedHttpResult artTimeout;
        Connection artBlocker = newConnection();
        try {
            artBlocker.setAutoCommit(false);
            lock(artBlocker, "select art_id from art where art_id = ? for update", artId);
            artTimeout = invokeProductionBid(token, artId, bidPrice);
        } finally {
            artBlocker.rollback();
            artBlocker.close();
        }
        assertBidConflict(artTimeout);
        assertThat(snapshot(artId, bidderId)).isEqualTo(initial);
        assertAllPooledSessions(globalBefore);

        TimedHttpResult accountTimeout;
        Connection accountBlocker = newConnection();
        try {
            accountBlocker.setAutoCommit(false);
            lock(
                    accountBlocker,
                    "select user_id from point_account where user_id = ? for update",
                    bidderId
            );
            accountTimeout = invokeProductionBid(token, artId, bidPrice);
        } finally {
            accountBlocker.rollback();
            accountBlocker.close();
        }
        assertBidConflict(accountTimeout);
        assertThat(snapshot(artId, bidderId)).isEqualTo(initial);
        assertAllPooledSessions(globalBefore);

        TimedHttpResult recovered = invokeProductionBid(token, artId, bidPrice);
        assertThat(recovered.status()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(recovered.body().path("artId").asLong()).isEqualTo(artId);
        assertThat(recovered.body().path("bidPrice").asInt()).isEqualTo(bidPrice);

        BidState completed = snapshot(artId, bidderId);
        assertThat(completed.bidCount()).isEqualTo(initial.bidCount() + 1);
        assertThat(completed.currentPrice()).isEqualTo(bidPrice);
        assertThat(completed.activePointHoldId()).isNotNull();
        assertThat(completed.activeHoldCount()).isEqualTo(1);
        long requiredHold = bidderId.equals(initial.activeHoldUserId())
                ? bidPrice - initial.activeHoldAmount()
                : bidPrice;
        assertThat(completed.availableBalance())
                .isEqualTo(initial.availableBalance() - requiredHold);
        assertThat(completed.heldBalance())
                .isEqualTo(initial.heldBalance() + requiredHold);
        assertThat(completed.ledgerCount()).isEqualTo(initial.ledgerCount() + 1);
        assertAllPooledSessions(globalBefore);

        long globalAfter = queryLong(newConnection(), "select @@global.innodb_lock_wait_timeout");
        assertThat(globalAfter).isEqualTo(globalBefore);

        System.out.printf(
                Locale.ROOT,
                "PRODUCTION_LOCK_TIMEOUT_DIAGNOSTIC timeoutSeconds=%d globalBefore=%d "
                        + "globalAfter=%d artElapsedMs=%d artHttp=%d "
                        + "pointAccountElapsedMs=%d pointAccountHttp=%d "
                        + "recoveryElapsedMs=%d recoveryHttp=%d initial=%s completed=%s%n",
                PRODUCTION_TIMEOUT_SECONDS,
                globalBefore,
                globalAfter,
                artTimeout.elapsedMillis(),
                artTimeout.status(),
                accountTimeout.elapsedMillis(),
                accountTimeout.status(),
                recovered.elapsedMillis(),
                recovered.status(),
                initial,
                completed
        );
    }

    private TimedHttpResult invokeProductionBid(
            String token,
            long artId,
            int bidPrice) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        long startedAt = System.nanoTime();
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/arts/" + artId + "/bids",
                HttpMethod.POST,
                new HttpEntity<>("{\"bidPrice\":" + bidPrice + "}", headers),
                String.class
        );
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        return new TimedHttpResult(
                response.getStatusCode().value(),
                elapsedMillis,
                objectMapper.readTree(response.getBody())
        );
    }

    private void assertBidConflict(TimedHttpResult result) {
        assertThat(result.status()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(result.body().path("status").asInt()).isEqualTo(409);
        assertThat(result.body().path("code").asText()).isEqualTo("BID_CONFLICT");
        assertThat(result.elapsedMillis()).isBetween(
                PRODUCTION_TIMEOUT_SECONDS * 1_000L - 250L,
                PRODUCTION_TIMEOUT_SECONDS * 1_000L + 1_500L
        );
    }

    private int nextBidPrice(long artId) {
        return jdbcTemplate.queryForObject(
                "select current_price + minimum_bid_increment from art where art_id = ?",
                Integer.class,
                artId
        );
    }

    private BidState snapshot(long artId, String bidderId) {
        return jdbcTemplate.queryForObject(
                """
                select
                    (select count(*) from bid where art_id = ?) as bid_count,
                    a.current_price,
                    a.active_point_hold_id,
                    (select user_id from point_hold
                     where hold_id = a.active_point_hold_id) as active_hold_user_id,
                    (select amount from point_hold
                     where hold_id = a.active_point_hold_id) as active_hold_amount,
                    (select count(*) from point_hold
                     where art_id = ? and status = 'HELD') as active_hold_count,
                    pa.available_balance,
                    pa.held_balance,
                    (select count(*) from point_transaction where user_id = ?) as ledger_count,
                    (select coalesce(sum(available_delta), 0)
                     from point_transaction where user_id = ?) as available_delta_sum,
                    (select coalesce(sum(held_delta), 0)
                     from point_transaction where user_id = ?) as held_delta_sum
                from art a
                join point_account pa on pa.user_id = ?
                where a.art_id = ?
                """,
                (resultSet, rowNumber) -> new BidState(
                        resultSet.getLong("bid_count"),
                        resultSet.getInt("current_price"),
                        resultSet.getObject("active_point_hold_id", Long.class),
                        resultSet.getString("active_hold_user_id"),
                        resultSet.getLong("active_hold_amount"),
                        resultSet.getLong("active_hold_count"),
                        resultSet.getLong("available_balance"),
                        resultSet.getLong("held_balance"),
                        resultSet.getLong("ledger_count"),
                        resultSet.getLong("available_delta_sum"),
                        resultSet.getLong("held_delta_sum")
                ),
                artId,
                artId,
                bidderId,
                bidderId,
                bidderId,
                bidderId,
                artId
        );
    }

    private void assertAllPooledSessions(long expectedTimeout) throws Exception {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        int maximumPoolSize = ((HikariDataSource) dataSource).getMaximumPoolSize();
        List<Connection> borrowed = new ArrayList<>();
        try {
            for (int index = 0; index < maximumPoolSize; index++) {
                Connection connection = dataSource.getConnection();
                borrowed.add(connection);
                assertThat(queryLongWithoutClosing(
                        connection,
                        "select @@session.innodb_lock_wait_timeout"
                )).isEqualTo(expectedTimeout);
            }
        } finally {
            for (Connection connection : borrowed) {
                connection.close();
            }
        }
    }

    private void assertLoadTestDatabase() throws Exception {
        try (Connection connection = newConnection()) {
            assertThat(connection.getCatalog()).matches(ALLOWED_DATABASE);
            try (PreparedStatement statement = connection.prepareStatement(
                    "select count(*) from load_test_schema_guard where marker = ?"
            )) {
                statement.setString(1, MARKER);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getInt(1)).isEqualTo(1);
                }
            }
        }
    }

    private long fixtureId(String sql) throws Exception {
        try (Connection connection = newConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    private DiagnosticResponse invoke(
            String token,
            String target,
            String mode,
            long artId,
            String bidderId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<DiagnosticResponse> response = restTemplate.exchange(
                "/__load-test/session-timeout?target=" + target
                        + "&mode=" + mode
                        + "&artId=" + artId
                        + "&bidderId=" + bidderId,
                HttpMethod.POST,
                new HttpEntity<>(headers),
                DiagnosticResponse.class
        );
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(response.getBody().httpStatus());
        ReuseResult reuse = findReusedConnection(response.getBody().transactionConnectionId());
        return response.getBody().withReuse(reuse.connectionId(), reuse.sessionValue());
    }

    private ReuseResult findReusedConnection(long expectedConnectionId) {
        int maximumPoolSize = dataSource instanceof HikariDataSource hikariDataSource
                ? hikariDataSource.getMaximumPoolSize()
                : 10;
        List<Connection> borrowed = new ArrayList<>();
        try {
            for (int attempt = 0; attempt < maximumPoolSize; attempt++) {
                Connection connection = dataSource.getConnection();
                borrowed.add(connection);
                long connectionId = queryLongWithoutClosing(
                        connection, "select connection_id()"
                );
                if (connectionId == expectedConnectionId) {
                    return new ReuseResult(
                            connectionId,
                            queryLongWithoutClosing(
                                    connection,
                                    "select @@session.innodb_lock_wait_timeout"
                            )
                    );
                }
            }
            throw new IllegalStateException(
                    "Transaction connection was not reused within the existing Hikari pool"
            );
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        } finally {
            for (Connection connection : borrowed) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    private long queryLongWithoutClosing(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                throw new SQLException("Query returned no row: " + sql);
            }
            return resultSet.getLong(1);
        }
    }

    private void assertRestored(
            DiagnosticResponse response,
            HttpStatus expectedStatus,
            String expectedOutcome) {
        assertThat(response.httpStatus()).isEqualTo(expectedStatus.value());
        assertThat(response.outcome()).isEqualTo(expectedOutcome);
        assertThat(response.transactionConnectionId()).isEqualTo(response.jpaConnectionId());
        assertThat(response.restoredSessionValue()).isEqualTo(response.originalSessionValue());
        assertThat(response.reusedConnectionId()).isEqualTo(response.transactionConnectionId());
        assertThat(response.reusedSessionValue()).isEqualTo(response.originalSessionValue());
    }

    private void assertTimeout(DiagnosticResponse response, String target) {
        assertRestored(response, HttpStatus.CONFLICT, "LOCK_TIMEOUT");
        assertThat(response.target()).isEqualTo(target);
        assertThat(response.elapsedMillis())
                .isBetween(TEST_TIMEOUT_SECONDS * 1_000L - 250L, TEST_TIMEOUT_SECONDS * 1_000L + 1_500L);
        assertThat(response.sqlState()).isNotBlank();
        assertThat(response.mysqlErrorCode()).isEqualTo(1205);
        assertThat(response.exceptionTypes()).isNotEmpty();
    }

    private Connection newConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private long queryLong(Connection connection, String sql) throws Exception {
        try (connection;
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    private void lock(Connection connection, String sql, Object value) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
            }
        }
    }

    @TestConfiguration
    static class DiagnosticConfiguration {

        @Bean
        DiagnosticRunner diagnosticRunner(
                EntityManager entityManager,
                DataSource dataSource,
                PlatformTransactionManager transactionManager) {
            return new DiagnosticRunner(entityManager, dataSource, transactionManager);
        }

        @Bean
        DiagnosticController diagnosticController(DiagnosticRunner runner) {
            return new DiagnosticController(runner);
        }
    }

    @RestController
    static class DiagnosticController {
        private final DiagnosticRunner runner;

        DiagnosticController(DiagnosticRunner runner) {
            this.runner = runner;
        }

        @PostMapping("/__load-test/session-timeout")
        ResponseEntity<DiagnosticResponse> run(
                @RequestParam String target,
                @RequestParam String mode,
                @RequestParam long artId,
                @RequestParam String bidderId) {
            DiagnosticResponse response = runner.run(target, mode, artId, bidderId);
            return ResponseEntity.status(response.httpStatus()).body(response);
        }
    }

    static class DiagnosticRunner {
        private final EntityManager entityManager;
        private final DataSource dataSource;
        private final TransactionTemplate transactionTemplate;

        DiagnosticRunner(
                EntityManager entityManager,
                DataSource dataSource,
                PlatformTransactionManager transactionManager) {
            this.entityManager = entityManager;
            this.dataSource = dataSource;
            this.transactionTemplate = new TransactionTemplate(transactionManager);
        }

        DiagnosticResponse run(String target, String mode, long artId, String bidderId) {
            MutableDiagnostic diagnostic = transactionTemplate.execute(status -> {
                MutableDiagnostic current = new MutableDiagnostic(target);
                try {
                    entityManager.unwrap(Session.class).doWork(connection -> {
                        current.transactionConnectionId = queryLong(
                                connection, "select connection_id()"
                        );
                        current.originalSessionValue = queryLong(
                                connection, "select @@session.innodb_lock_wait_timeout"
                        );
                        execute(connection, "set session innodb_lock_wait_timeout = "
                                + TEST_TIMEOUT_SECONDS);
                        current.configuredSessionValue = queryLong(
                                connection, "select @@session.innodb_lock_wait_timeout"
                        );
                    });
                    current.jpaConnectionId = ((Number) entityManager.createNativeQuery(
                            "select connection_id()"
                    ).getSingleResult()).longValue();

                    long startedAt = System.nanoTime();
                    try {
                        if ("domain_failure".equals(mode)) {
                            throw new DiagnosticDomainFailure();
                        }
                        if ("art".equals(target)) {
                            entityManager.createNativeQuery(
                                            "select art_id from art where art_id = :id for update"
                                    )
                                    .setParameter("id", artId)
                                    .getSingleResult();
                        } else if ("point_account".equals(target)) {
                            entityManager.createNativeQuery(
                                            "select user_id from point_account "
                                                    + "where user_id = :id for update"
                                    )
                                    .setParameter("id", bidderId)
                                    .getSingleResult();
                        } else {
                            throw new IllegalArgumentException("Unsupported target: " + target);
                        }
                        current.httpStatus = HttpStatus.OK.value();
                        current.outcome = "SUCCESS";
                    } catch (DiagnosticDomainFailure exception) {
                        current.httpStatus = HttpStatus.UNPROCESSABLE_ENTITY.value();
                        current.outcome = "DOMAIN_FAILURE";
                        current.capture(exception);
                    } catch (RuntimeException exception) {
                        current.capture(exception);
                        current.httpStatus = current.mysqlErrorCode == 1205
                                ? HttpStatus.CONFLICT.value()
                                : HttpStatus.INTERNAL_SERVER_ERROR.value();
                        current.outcome = current.mysqlErrorCode == 1205
                                ? "LOCK_TIMEOUT"
                                : "UNEXPECTED_ERROR";
                    } finally {
                        current.elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
                    }
                } finally {
                    entityManager.unwrap(Session.class).doWork(connection -> {
                        execute(
                                connection,
                                "set session innodb_lock_wait_timeout = "
                                        + current.originalSessionValue
                        );
                        current.restoredSessionValue = queryLong(
                                connection, "select @@session.innodb_lock_wait_timeout"
                        );
                    });
                    status.setRollbackOnly();
                }
                return current;
            });

            if (diagnostic == null) {
                throw new IllegalStateException("Transaction did not return diagnostics");
            }
            return diagnostic.toResponse();
        }

        private static long queryLong(Connection connection, String sql) throws SQLException {
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(sql)) {
                if (!resultSet.next()) {
                    throw new SQLException("Query returned no row: " + sql);
                }
                return resultSet.getLong(1);
            }
        }

        private static void execute(Connection connection, String sql) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }

    static class MutableDiagnostic {
        final String target;
        int httpStatus;
        String outcome;
        long elapsedMillis;
        long transactionConnectionId;
        long jpaConnectionId;
        long originalSessionValue;
        long configuredSessionValue;
        long restoredSessionValue;
        long reusedConnectionId;
        long reusedSessionValue;
        String sqlState;
        int mysqlErrorCode;
        List<String> exceptionTypes = List.of();

        MutableDiagnostic(String target) {
            this.target = target;
        }

        void capture(Throwable throwable) {
            List<String> types = new ArrayList<>();
            Throwable current = throwable;
            while (current != null && types.size() < 12) {
                types.add(current.getClass().getName());
                if (current instanceof SQLException sqlException) {
                    sqlState = sqlException.getSQLState();
                    mysqlErrorCode = sqlException.getErrorCode();
                }
                current = current.getCause();
            }
            exceptionTypes = List.copyOf(types);
        }

        DiagnosticResponse toResponse() {
            return new DiagnosticResponse(
                    target,
                    httpStatus,
                    outcome,
                    elapsedMillis,
                    transactionConnectionId,
                    jpaConnectionId,
                    originalSessionValue,
                    configuredSessionValue,
                    restoredSessionValue,
                    reusedConnectionId,
                    reusedSessionValue,
                    sqlState,
                    mysqlErrorCode,
                    exceptionTypes
            );
        }
    }

    static class DiagnosticDomainFailure extends RuntimeException {
    }

    record ReuseResult(long connectionId, long sessionValue) {
    }

    record TimedHttpResult(int status, long elapsedMillis, JsonNode body) {
    }

    record BidState(
            long bidCount,
            int currentPrice,
            Long activePointHoldId,
            String activeHoldUserId,
            long activeHoldAmount,
            long activeHoldCount,
            long availableBalance,
            long heldBalance,
            long ledgerCount,
            long availableDeltaSum,
            long heldDeltaSum) {
    }

    record DiagnosticResponse(
            String target,
            int httpStatus,
            String outcome,
            long elapsedMillis,
            long transactionConnectionId,
            long jpaConnectionId,
            long originalSessionValue,
            long configuredSessionValue,
            long restoredSessionValue,
            long reusedConnectionId,
            long reusedSessionValue,
            String sqlState,
            int mysqlErrorCode,
            List<String> exceptionTypes) {

        DiagnosticResponse withReuse(long connectionId, long sessionValue) {
            return new DiagnosticResponse(
                    target,
                    httpStatus,
                    outcome,
                    elapsedMillis,
                    transactionConnectionId,
                    jpaConnectionId,
                    originalSessionValue,
                    configuredSessionValue,
                    restoredSessionValue,
                    connectionId,
                    sessionValue,
                    sqlState,
                    mysqlErrorCode,
                    exceptionTypes
            );
        }
    }
}
