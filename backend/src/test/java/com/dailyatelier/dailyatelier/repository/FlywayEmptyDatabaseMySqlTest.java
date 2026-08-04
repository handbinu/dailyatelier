package com.dailyatelier.dailyatelier.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=true",
        "spring.flyway.baseline-version=0",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@EnabledIfEnvironmentVariable(
        named = "DAILYATELIER_EMPTY_DB_TEST",
        matches = "true"
)
class FlywayEmptyDatabaseMySqlTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesLatestSchemaAndHibernateValidationStarts() {
        assertThat(appliedVersions()).containsExactly(
                "0", "1", "2", "3", "4", "5", "6"
        );
        assertThat(jdbcTemplate.queryForObject("""
                select type
                from flyway_schema_history
                where version = '0'
                """, String.class)).isEqualTo("SQL");
        assertThat(tableNames()).contains(
                "users",
                "artist",
                "art",
                "bid",
                "orders",
                "address",
                "inquiry",
                "likes",
                "review",
                "point_account",
                "point_transaction",
                "point_hold",
                "point_charge",
                "payment_callback_event"
        );
    }

    @Test
    void secondMigrationExecutesNothingAndRequiredConstraintsExist() {
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(uniqueIndexCount("orders", "art_id")).isEqualTo(1);
        assertThat(indexCount(
                "art",
                "idx_art_close_candidates"
        )).isEqualTo(3);
        assertThat(indexCount(
                "orders",
                "idx_orders_buyer_status_created"
        )).isEqualTo(3);
        assertThat(indexCount(
                "orders",
                "idx_orders_seller_status_created"
        )).isEqualTo(3);
        assertThat(indexCount(
                "orders",
                "idx_orders_payment_expiration"
        )).isEqualTo(3);
        assertThat(indexCount(
                "point_transaction",
                "idx_point_transaction_user_created"
        )).isEqualTo(3);
        assertThat(foreignKeyCount(
                "art",
                "active_point_hold_id",
                "point_hold",
                "hold_id"
        )).isEqualTo(1);
        assertThat(foreignKeyCount(
                "orders",
                "buyer_id",
                "users",
                "user_id"
        )).isEqualTo(1);
        assertThat(checkConstraintCount()).isGreaterThanOrEqualTo(5);
    }

    private List<String> appliedVersions() {
        return Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .map(Object::toString)
                .toList();
    }

    private List<String> tableNames() {
        return jdbcTemplate.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = database()
                """, String.class);
    }

    private int uniqueIndexCount(String tableName, String columnName) {
        return jdbcTemplate.queryForObject("""
                select count(distinct index_name)
                from information_schema.statistics
                where table_schema = database()
                  and table_name = ?
                  and column_name = ?
                  and non_unique = 0
                """, Integer.class, tableName, columnName);
    }

    private int indexCount(String tableName, String indexName) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.statistics
                where table_schema = database()
                  and table_name = ?
                  and index_name = ?
                """, Integer.class, tableName, indexName);
    }

    private int foreignKeyCount(
            String tableName,
            String columnName,
            String referencedTable,
            String referencedColumn) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.key_column_usage
                where table_schema = database()
                  and table_name = ?
                  and column_name = ?
                  and referenced_table_name = ?
                  and referenced_column_name = ?
                """,
                Integer.class,
                tableName,
                columnName,
                referencedTable,
                referencedColumn
        );
    }

    private int checkConstraintCount() {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.table_constraints
                where table_schema = database()
                  and constraint_type = 'CHECK'
                """, Integer.class);
    }
}
