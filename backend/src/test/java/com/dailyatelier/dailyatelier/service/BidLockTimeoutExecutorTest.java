package com.dailyatelier.dailyatelier.service;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.jdbc.Work;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidLockTimeoutExecutorTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Session session;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData databaseMetaData;

    @Mock
    private Statement connectionIdStatement;

    @Mock
    private Statement timeoutStatement;

    @Mock
    private Statement configureStatement;

    @Mock
    private Statement restoreConnectionIdStatement;

    @Mock
    private Statement restoreStatement;

    @Mock
    private ResultSet connectionIdResult;

    @Mock
    private ResultSet timeoutResult;

    @Mock
    private ResultSet restoreConnectionIdResult;

    private BidLockTimeoutExecutor executor;

    @BeforeEach
    void setUp() throws Exception {
        executor = new BidLockTimeoutExecutor(entityManager);
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        doAnswer(invocation -> {
            Work work = invocation.getArgument(0);
            work.execute(connection);
            return null;
        }).when(session).doWork(org.mockito.ArgumentMatchers.any());
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");
        when(connection.createStatement()).thenReturn(
                connectionIdStatement,
                timeoutStatement,
                configureStatement,
                restoreConnectionIdStatement,
                restoreStatement
        );
        stubLongQuery(connectionIdStatement, connectionIdResult, 17L);
        stubLongQuery(timeoutStatement, timeoutResult, 50L);
        stubLongQuery(restoreConnectionIdStatement, restoreConnectionIdResult, 17L);
    }

    @Test
    void restoresOriginalSessionTimeoutWhenBidProcessingFails() throws Exception {
        IllegalStateException failure = new IllegalStateException("bid failed");

        assertThatThrownBy(() -> executor.execute(() -> {
            throw failure;
        })).isSameAs(failure);

        var ordered = inOrder(configureStatement, restoreStatement);
        ordered.verify(configureStatement)
                .execute("set session innodb_lock_wait_timeout = 3");
        ordered.verify(restoreStatement)
                .execute("set session innodb_lock_wait_timeout = 50");
    }

    private void stubLongQuery(Statement statement, ResultSet resultSet, long value)
            throws Exception {
        when(statement.executeQuery(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong(1)).thenReturn(value);
    }
}
