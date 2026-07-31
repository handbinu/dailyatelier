package com.dailyatelier.dailyatelier.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(
        named = "DAILYATELIER_MYSQL_SCHEMA_TEST",
        matches = "true"
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PointLedgerMySqlSchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    private Flyway flyway;

    @BeforeAll
    void repairAndMigrate() {
        flyway = Flyway.configure()
                .dataSource(dataSource)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
        flyway.repair();
        flyway.migrate();
    }

    @Test
    void pointTablesHaveRequiredConstraintsAndIndexes() {
        assertUniqueIndex(
                "point_transaction",
                "idempotency_key"
        );
        assertUniqueIndex(
                "point_transaction",
                "reversal_of_transaction_id,type"
        );
        assertIndex(
                "point_transaction",
                "user_id,created_at,transaction_id"
        );
        assertIndex(
                "point_transaction",
                "reference_type,reference_id,type"
        );
        assertIndex("point_hold", "art_id,created_at");
        assertIndex("point_hold", "user_id,status,created_at");
        assertUniqueIndex("point_charge", "merchant_order_id");
        assertUniqueIndex("point_charge", "provider,pg_order_id");
        assertUniqueIndex(
                "point_charge",
                "user_id,idempotency_key"
        );
        assertForeignKey(
                "point_account",
                "user_id",
                "users",
                "user_id"
        );
        assertForeignKey(
                "point_transaction",
                "user_id",
                "users",
                "user_id"
        );
        assertForeignKey(
                "point_transaction",
                "reversal_of_transaction_id",
                "point_transaction",
                "transaction_id"
        );
        assertForeignKey(
                "point_hold",
                "commit_order_id",
                "orders",
                "order_id"
        );
        assertForeignKey(
                "point_charge",
                "user_id",
                "users",
                "user_id"
        );
        assertForeignKey(
                "point_charge",
                "charge_transaction_id",
                "point_transaction",
                "transaction_id"
        );
        assertForeignKey(
                "point_charge",
                "refund_transaction_id",
                "point_transaction",
                "transaction_id"
        );

        Integer checkConstraintCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.table_constraints
                where table_schema = database()
                  and table_name in (
                      'point_account',
                      'point_transaction',
                      'point_hold',
                      'point_charge'
                  )
                  and constraint_type = 'CHECK'
                """, Integer.class);
        assertThat(checkConstraintCount).isGreaterThanOrEqualTo(4);
    }

    @Test
    void migrationIsIdempotentAndEveryAccountMatchesLedgerSum() {
        long accountCountBefore = count("point_account");
        long transactionCountBefore = count("point_transaction");

        MigrateResult result = flyway.migrate();

        assertThat(result.migrationsExecuted).isZero();
        assertThat(count("point_account")).isEqualTo(accountCountBefore);
        assertThat(count("point_transaction"))
                .isEqualTo(transactionCountBefore);

        Integer mismatchCount = jdbcTemplate.queryForObject("""
                select count(*)
                from point_account account
                left join (
                    select
                        user_id,
                        sum(available_delta) as available_sum,
                        sum(held_delta) as held_sum
                    from point_transaction
                    group by user_id
                ) ledger on ledger.user_id = account.user_id
                where account.available_balance <>
                        coalesce(ledger.available_sum, 0)
                   or account.held_balance <>
                        coalesce(ledger.held_sum, 0)
                """, Integer.class);
        assertThat(mismatchCount).isZero();

        Integer missingAccountCount = jdbcTemplate.queryForObject("""
                select count(*)
                from users u
                left join point_account account
                    on account.user_id = u.user_id
                where account.user_id is null
                """, Integer.class);
        assertThat(missingAccountCount).isZero();
    }

    private long count(String tableName) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + tableName,
                Long.class
        );
    }

    private void assertUniqueIndex(String tableName, String columns) {
        assertThat(indexes(tableName)).anySatisfy(index -> {
            assertThat(index.get("columns_in_order")).isEqualTo(columns);
            assertThat(((Number) index.get("non_unique")).intValue()).isZero();
        });
    }

    private void assertIndex(String tableName, String columns) {
        assertThat(indexes(tableName)).anySatisfy(index ->
                assertThat(index.get("columns_in_order")).isEqualTo(columns));
    }

    private List<Map<String, Object>> indexes(String tableName) {
        return jdbcTemplate.queryForList("""
                select
                    index_name,
                    non_unique,
                    group_concat(
                        column_name
                        order by seq_in_index
                        separator ','
                    ) as columns_in_order
                from information_schema.statistics
                where table_schema = database()
                  and table_name = ?
                group by index_name, non_unique
                """, tableName);
    }

    private void assertForeignKey(
            String tableName,
            String columnName,
            String referencedTable,
            String referencedColumn) {
        Integer count = jdbcTemplate.queryForObject("""
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
        assertThat(count).isEqualTo(1);
    }
}
