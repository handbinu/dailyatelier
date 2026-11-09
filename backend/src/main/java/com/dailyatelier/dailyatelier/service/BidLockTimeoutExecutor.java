package com.dailyatelier.dailyatelier.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class BidLockTimeoutExecutor {
    private static final int LOCK_WAIT_TIMEOUT_SECONDS = 3;

    private final EntityManager entityManager;

    public <T> T execute(Supplier<T> action) {
        Session session = entityManager.unwrap(Session.class);
        SessionTimeoutState state = new SessionTimeoutState();
        session.doWork(connection -> configureTimeout(connection, state));
        if (!state.configured) {
            return action.get();
        }

        RuntimeException actionFailure = null;
        try {
            return action.get();
        } catch (RuntimeException exception) {
            actionFailure = exception;
            throw exception;
        } finally {
            try {
                session.doWork(connection -> restoreTimeout(connection, state));
            } catch (RuntimeException restoreFailure) {
                if (actionFailure != null) {
                    actionFailure.addSuppressed(restoreFailure);
                } else {
                    throw restoreFailure;
                }
            }
        }
    }

    private void configureTimeout(Connection connection, SessionTimeoutState state)
            throws SQLException {
        if (!"MySQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
            return;
        }
        state.connectionId = queryLong(connection, "select connection_id()");
        state.originalTimeoutSeconds = queryLong(
                connection,
                "select @@session.innodb_lock_wait_timeout"
        );
        execute(connection, "set session innodb_lock_wait_timeout = "
                + LOCK_WAIT_TIMEOUT_SECONDS);
        state.configured = true;
    }

    private void restoreTimeout(Connection connection, SessionTimeoutState state)
            throws SQLException {
        long restoreConnectionId = queryLong(connection, "select connection_id()");
        if (restoreConnectionId != state.connectionId) {
            throw new SQLException("Bid lock timeout connection changed during transaction");
        }
        execute(connection, "set session innodb_lock_wait_timeout = "
                + state.originalTimeoutSeconds);
    }

    private long queryLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                throw new SQLException("Query returned no row: " + sql);
            }
            return resultSet.getLong(1);
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static class SessionTimeoutState {
        private boolean configured;
        private long connectionId;
        private long originalTimeoutSeconds;
    }
}
