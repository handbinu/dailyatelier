package com.dailyatelier.dailyatelier.support;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TestDatabaseSafetyInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String MYSQL_PROPERTIES = "mysqlIntegrationTestDatasource";
    private static final List<String> MYSQL_OPT_IN_FLAGS = List.of(
            "DAILYATELIER_EMPTY_DB_TEST",
            "DAILYATELIER_MYSQL_SCHEMA_TEST",
            "DAILYATELIER_LOAD_TEST_FIXTURE",
            "DAILYATELIER_SESSION_TIMEOUT_TEST"
    );

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Map<String, String> environment = System.getenv();
        if (isMySqlOptedIn(environment)) {
            applicationContext.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource(
                            MYSQL_PROPERTIES,
                            mysqlDatasourceProperties(environment)
                    )
            );
            return;
        }

        requireGeneralTestH2(
                applicationContext.getEnvironment().getProperty("spring.datasource.url")
        );
    }

    static void requireGeneralTestH2(String datasourceUrl) {
        if (datasourceUrl == null || !datasourceUrl.startsWith("jdbc:h2:mem:")) {
            throw new IllegalStateException(
                    "General tests must use the isolated in-memory H2 datasource."
            );
        }
    }

    static boolean isMySqlOptedIn(Map<String, String> environment) {
        return MYSQL_OPT_IN_FLAGS.stream()
                .anyMatch(flag -> "true".equalsIgnoreCase(environment.get(flag)));
    }

    static Map<String, Object> mysqlDatasourceProperties(Map<String, String> environment) {
        String datasourceUrl = requireValue(environment, "DB_URL");
        String username = requireValue(environment, "DB_USERNAME");
        String password = requireValue(environment, "DB_PASSWORD");
        validateDedicatedLocalMySql(datasourceUrl);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", datasourceUrl);
        properties.put("spring.datasource.username", username);
        properties.put("spring.datasource.password", password);
        properties.put("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        return properties;
    }

    static void validateDedicatedLocalMySql(String datasourceUrl) {
        if (!datasourceUrl.startsWith("jdbc:mysql://")) {
            throw new IllegalStateException(
                    "Opt-in MySQL tests require a jdbc:mysql datasource."
            );
        }

        URI uri = URI.create(datasourceUrl.substring("jdbc:".length()));
        String host = uri.getHost();
        if (host == null || !(host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1"))) {
            throw new IllegalStateException(
                    "Opt-in MySQL tests require a localhost datasource."
            );
        }

        String path = uri.getPath();
        String database = path == null || path.length() <= 1 ? "" : path.substring(1);
        if (database.isBlank()) {
            throw new IllegalStateException(
                    "Opt-in MySQL tests require a dedicated database name."
            );
        }
        if ("dailyatelier".equals(database.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "The dailyatelier development database cannot be used by MySQL tests."
            );
        }
    }

    private static String requireValue(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for opt-in MySQL tests.");
        }
        return value;
    }
}
