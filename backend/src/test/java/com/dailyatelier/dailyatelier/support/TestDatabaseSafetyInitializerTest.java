package com.dailyatelier.dailyatelier.support;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDatabaseSafetyInitializerTest {

    @Test
    void generalTestsAcceptOnlyInMemoryH2() {
        assertThatNoException().isThrownBy(() ->
                TestDatabaseSafetyInitializer.requireGeneralTestH2(
                        "jdbc:h2:mem:dailyatelier-test;MODE=MySQL"
                )
        );
        assertThatThrownBy(() ->
                TestDatabaseSafetyInitializer.requireGeneralTestH2(
                        "jdbc:mysql://localhost:3306/dailyatelier"
                )
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("General tests");
    }

    @Test
    void mysqlTestsRequireExplicitOptIn() {
        assertThat(TestDatabaseSafetyInitializer.isMySqlOptedIn(Map.of())).isFalse();
        assertThat(TestDatabaseSafetyInitializer.isMySqlOptedIn(Map.of(
                "DAILYATELIER_MYSQL_SCHEMA_TEST", "true"
        ))).isTrue();
    }

    @Test
    void optedInMysqlRejectsDevelopmentDatabase() {
        Map<String, String> environment = mysqlEnvironment(
                "jdbc:mysql://localhost:3306/dailyatelier?serverTimezone=Asia/Seoul"
        );
        assertThatThrownBy(() ->
                TestDatabaseSafetyInitializer.mysqlDatasourceProperties(environment)
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("development database");
    }

    @Test
    void optedInMysqlRejectsRemoteDatabase() {
        Map<String, String> environment = mysqlEnvironment(
                "jdbc:mysql://db.example.com:3306/dailyatelier_test"
        );
        assertThatThrownBy(() ->
                TestDatabaseSafetyInitializer.mysqlDatasourceProperties(environment)
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("localhost");
    }

    @Test
    void optedInMysqlUsesDedicatedLocalDatabase() {
        Map<String, String> environment = mysqlEnvironment(
                "jdbc:mysql://127.0.0.1:3306/dailyatelier_schema_test"
        );
        Map<String, Object> properties =
                TestDatabaseSafetyInitializer.mysqlDatasourceProperties(environment);

        assertThat(properties).containsEntry(
                "spring.datasource.url",
                "jdbc:mysql://127.0.0.1:3306/dailyatelier_schema_test"
        );
        assertThat(properties).containsEntry(
                "spring.datasource.driver-class-name",
                "com.mysql.cj.jdbc.Driver"
        );
    }

    private Map<String, String> mysqlEnvironment(String datasourceUrl) {
        Map<String, String> environment = new HashMap<>();
        environment.put("DAILYATELIER_MYSQL_SCHEMA_TEST", "true");
        environment.put("DB_URL", datasourceUrl);
        environment.put("DB_USERNAME", "tester");
        environment.put("DB_PASSWORD", "test-password");
        return environment;
    }
}
