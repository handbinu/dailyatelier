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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@EnabledIfEnvironmentVariable(
        named = "DAILYATELIER_EMPTY_DB_TEST",
        matches = "true"
)
class FlywayEmptyDatabaseMySqlTest {

    private static final Set<String> EXPECTED_TABLES = Set.of(
            "users", "artist", "art", "bid", "orders", "address",
            "inquiry", "likes", "review", "point_account",
            "point_transaction", "point_hold", "point_charge",
            "payment_callback_event"
    );

    private static final Map<String, Integer> EXPECTED_COLUMN_COUNTS = Map.ofEntries(
            Map.entry("users", 10),
            Map.entry("artist", 6),
            Map.entry("art", 18),
            Map.entry("bid", 5),
            Map.entry("orders", 44),
            Map.entry("address", 4),
            Map.entry("inquiry", 7),
            Map.entry("likes", 3),
            Map.entry("review", 5),
            Map.entry("point_account", 6),
            Map.entry("point_transaction", 15),
            Map.entry("point_hold", 12),
            Map.entry("point_charge", 18),
            Map.entry("payment_callback_event", 10)
    );

    private static final Set<String> EXPECTED_INDEXES = Set.of(
            "idx_art_close_candidates",
            "idx_art_public_search",
            "idx_art_created",
            "idx_art_current_price",
            "idx_orders_buyer_status_created",
            "idx_orders_seller_status_created",
            "idx_orders_payment_expiration",
            "idx_point_transaction_user_created",
            "idx_point_transaction_reference",
            "idx_point_hold_art_created",
            "idx_point_hold_user_status_created",
            "idx_callback_status_received"
    );

    private static final Map<String, String> EXPECTED_INDEX_COLUMNS = Map.ofEntries(
            Map.entry("idx_art_public_search", "art_status,format,category,closing_time,art_id"),
            Map.entry("idx_art_created", "created_at,art_id"),
            Map.entry("idx_art_current_price", "current_price,art_id"),
            Map.entry("idx_art_close_candidates", "art_status,closing_time,art_id"),
            Map.entry("idx_orders_buyer_status_created", "buyer_id,status,created_at"),
            Map.entry("idx_orders_seller_status_created", "seller_id,status,created_at"),
            Map.entry("idx_orders_payment_expiration", "status,payment_due_at,order_id"),
            Map.entry("idx_point_transaction_user_created", "user_id,created_at,transaction_id"),
            Map.entry("idx_point_transaction_reference", "reference_type,reference_id,type"),
            Map.entry("idx_point_hold_art_created", "art_id,created_at"),
            Map.entry("idx_point_hold_user_status_created", "user_id,status,created_at"),
            Map.entry("idx_callback_status_received", "status,received_at,callback_event_id")
    );

    private static final Set<String> EXPECTED_UNIQUE_CONSTRAINTS = Set.of(
            "uq_artist_user",
            "uq_orders_art",
            "uq_art_active_point_hold",
            "uq_point_transaction_idempotency",
            "uq_point_transaction_reversal_type",
            "uq_point_charge_merchant_order",
            "uq_point_charge_provider_pg_order",
            "uq_point_charge_user_idempotency",
            "uq_callback_provider_event"
    );

    private static final Map<String, String> EXPECTED_UNIQUE_COLUMNS = Map.of(
            "uq_artist_user", "user_id",
            "uq_orders_art", "art_id",
            "uq_art_active_point_hold", "active_point_hold_id",
            "uq_point_transaction_idempotency", "idempotency_key",
            "uq_point_transaction_reversal_type", "reversal_of_transaction_id,type",
            "uq_point_charge_merchant_order", "merchant_order_id",
            "uq_point_charge_provider_pg_order", "provider,pg_order_id",
            "uq_point_charge_user_idempotency", "user_id,idempotency_key",
            "uq_callback_provider_event", "provider,provider_event_id"
    );

    private static final Set<String> EXPECTED_FOREIGN_KEYS = Set.of(
            "fk_artist_user", "fk_art_artist", "fk_bid_user", "fk_bid_art",
            "fk_art_winning_bid", "fk_orders_art", "fk_orders_winning_bid",
            "fk_orders_buyer", "fk_orders_seller", "fk_address_user",
            "fk_inquiry_user", "fk_likes_user", "fk_likes_art",
            "fk_review_user", "fk_review_art", "fk_point_account_user",
            "fk_point_transaction_user", "fk_point_transaction_reversal",
            "fk_point_hold_art", "fk_point_hold_user",
            "fk_point_hold_latest_bid", "fk_point_hold_commit_order",
            "fk_art_active_point_hold", "fk_point_charge_user",
            "fk_point_charge_transaction",
            "fk_point_charge_refund_transaction"
    );

    private static final Map<String, String> EXPECTED_FOREIGN_KEY_REFERENCES = Map.ofEntries(
            Map.entry("fk_artist_user", "artist.user_id->users.user_id"),
            Map.entry("fk_art_artist", "art.artist_code->artist.artist_code"),
            Map.entry("fk_bid_user", "bid.user_id->users.user_id"),
            Map.entry("fk_bid_art", "bid.art_id->art.art_id"),
            Map.entry("fk_art_winning_bid", "art.winning_bid_id->bid.bid_id"),
            Map.entry("fk_orders_art", "orders.art_id->art.art_id"),
            Map.entry("fk_orders_winning_bid", "orders.winning_bid_id->bid.bid_id"),
            Map.entry("fk_orders_buyer", "orders.buyer_id->users.user_id"),
            Map.entry("fk_orders_seller", "orders.seller_id->users.user_id"),
            Map.entry("fk_address_user", "address.user_id->users.user_id"),
            Map.entry("fk_inquiry_user", "inquiry.user_id->users.user_id"),
            Map.entry("fk_likes_user", "likes.user_id->users.user_id"),
            Map.entry("fk_likes_art", "likes.art_id->art.art_id"),
            Map.entry("fk_review_user", "review.user_id->users.user_id"),
            Map.entry("fk_review_art", "review.art_id->art.art_id"),
            Map.entry("fk_point_account_user", "point_account.user_id->users.user_id"),
            Map.entry("fk_point_transaction_user", "point_transaction.user_id->users.user_id"),
            Map.entry("fk_point_transaction_reversal", "point_transaction.reversal_of_transaction_id->point_transaction.transaction_id"),
            Map.entry("fk_point_hold_art", "point_hold.art_id->art.art_id"),
            Map.entry("fk_point_hold_user", "point_hold.user_id->users.user_id"),
            Map.entry("fk_point_hold_latest_bid", "point_hold.latest_bid_id->bid.bid_id"),
            Map.entry("fk_point_hold_commit_order", "point_hold.commit_order_id->orders.order_id"),
            Map.entry("fk_art_active_point_hold", "art.active_point_hold_id->point_hold.hold_id"),
            Map.entry("fk_point_charge_user", "point_charge.user_id->users.user_id"),
            Map.entry("fk_point_charge_transaction", "point_charge.charge_transaction_id->point_transaction.transaction_id"),
            Map.entry("fk_point_charge_refund_transaction", "point_charge.refund_transaction_id->point_transaction.transaction_id")
    );

    private static final Set<String> EXPECTED_CHECK_CONSTRAINTS = Set.of(
            "chk_point_account_available",
            "chk_point_account_held",
            "chk_point_transaction_amount",
            "chk_point_transaction_available_after",
            "chk_point_transaction_held_after",
            "chk_point_hold_amount",
            "chk_point_charge_requested_amount",
            "chk_point_charge_paid_amount",
            "chk_callback_attempt_count"
    );

    private static final Map<String, String> EXPECTED_CHECK_CLAUSES = Map.of(
            "chk_point_account_available", "available_balance>=0",
            "chk_point_account_held", "held_balance>=0",
            "chk_point_transaction_amount", "amount>0",
            "chk_point_transaction_available_after", "available_balance_after>=0",
            "chk_point_transaction_held_after", "held_balance_after>=0",
            "chk_point_hold_amount", "amount>0",
            "chk_point_charge_requested_amount", "requested_amount>0",
            "chk_point_charge_paid_amount", "paid_amount>=0",
            "chk_callback_attempt_count", "attempt_count>=0"
    );

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesLatestSchemaAndHibernateValidationStarts() {
        assertThat(appliedVersions()).containsExactly("1", "2", "3");
        assertThat(tableNames()).isEqualTo(EXPECTED_TABLES);
        assertThat(columnCounts()).isEqualTo(EXPECTED_COLUMN_COUNTS);
        assertThat(constraintCount("PRIMARY KEY")).isEqualTo(14);
        assertThat(constraintNames("UNIQUE"))
                .isEqualTo(EXPECTED_UNIQUE_CONSTRAINTS);
        assertThat(uniqueConstraintColumns())
                .isEqualTo(EXPECTED_UNIQUE_COLUMNS);
        assertThat(constraintNames("FOREIGN KEY"))
                .isEqualTo(EXPECTED_FOREIGN_KEYS);
        assertThat(foreignKeyReferences())
                .isEqualTo(EXPECTED_FOREIGN_KEY_REFERENCES);
        assertThat(constraintNames("CHECK"))
                .isEqualTo(EXPECTED_CHECK_CONSTRAINTS);
        assertThat(checkClauses()).isEqualTo(EXPECTED_CHECK_CLAUSES);
        assertThat(expectedIndexNames()).isEqualTo(EXPECTED_INDEXES);
        assertThat(expectedIndexColumns()).isEqualTo(EXPECTED_INDEX_COLUMNS);
        assertImportantColumn("users", "reserve", "int", false, "0");
        assertImportantColumn("art", "active_point_hold_id", "bigint", true, null);
        assertImportantColumn("art", "format", "varchar", false, null);
        assertImportantColumn("art", "category", "varchar", false, null);
        assertImportantColumn("art", "created_at", "datetime", false, null);
        assertImportantColumn("orders", "payment_method", "varchar", false, null);
        assertImportantColumn("orders", "refund_request_status", "varchar", true, null);
    }

    @Test
    void secondMigrationExecutesNothingAndBusinessTablesAreEmpty() {
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        for (String tableName : EXPECTED_TABLES) {
            assertThat(count(tableName)).as(tableName).isZero();
        }
    }

    private List<String> appliedVersions() {
        return Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .map(Object::toString)
                .toList();
    }

    private Set<String> tableNames() {
        return Set.copyOf(jdbcTemplate.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = database()
                  and table_name <> 'flyway_schema_history'
                """, String.class));
    }

    private Map<String, Integer> columnCounts() {
        return jdbcTemplate.query("""
                select table_name, count(*) as column_count
                from information_schema.columns
                where table_schema = database()
                  and table_name <> 'flyway_schema_history'
                group by table_name
                """, resultSet -> {
            Map<String, Integer> result = new java.util.HashMap<>();
            while (resultSet.next()) {
                result.put(
                        resultSet.getString("table_name"),
                        resultSet.getInt("column_count")
                );
            }
            return result;
        });
    }

    private Set<String> constraintNames(String type) {
        return Set.copyOf(jdbcTemplate.queryForList("""
                select constraint_name
                from information_schema.table_constraints
                where table_schema = database()
                  and constraint_type = ?
                  and table_name <> 'flyway_schema_history'
                """, String.class, type));
    }

    private int constraintCount(String type) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.table_constraints
                where table_schema = database()
                  and constraint_type = ?
                  and table_name <> 'flyway_schema_history'
                """, Integer.class, type);
    }

    private Map<String, String> uniqueConstraintColumns() {
        return queryNameValueMap("""
                select constraint_name,
                       group_concat(column_name order by ordinal_position separator ',') as value
                from information_schema.key_column_usage
                where table_schema = database()
                  and constraint_name in (
                      select constraint_name
                      from information_schema.table_constraints
                      where table_schema = database()
                        and constraint_type = 'UNIQUE'
                  )
                group by constraint_name
                """, "constraint_name");
    }

    private Map<String, String> foreignKeyReferences() {
        return queryNameValueMap("""
                select constraint_name,
                       concat(table_name, '.', column_name, '->',
                              referenced_table_name, '.', referenced_column_name) as value
                from information_schema.key_column_usage
                where table_schema = database()
                  and referenced_table_name is not null
                """, "constraint_name");
    }

    private Map<String, String> checkClauses() {
        Map<String, String> clauses = queryNameValueMap("""
                select constraint_name, check_clause as value
                from information_schema.check_constraints
                where constraint_schema = database()
                """, "constraint_name");
        clauses.replaceAll((name, clause) -> clause
                .replace("`", "")
                .replace(" ", "")
                .replace("(", "")
                .replace(")", "")
                .toLowerCase());
        return clauses;
    }

    private Set<String> expectedIndexNames() {
        String placeholders = String.join(",", EXPECTED_INDEXES.stream()
                .map(ignored -> "?")
                .toList());
        return Set.copyOf(jdbcTemplate.queryForList(
                "select distinct index_name from information_schema.statistics "
                        + "where table_schema = database() and index_name in ("
                        + placeholders + ")",
                String.class,
                EXPECTED_INDEXES.toArray()
        ));
    }

    private Map<String, String> expectedIndexColumns() {
        String placeholders = String.join(",", EXPECTED_INDEXES.stream()
                .map(ignored -> "?")
                .toList());
        return queryNameValueMap(
                "select index_name, group_concat(column_name order by seq_in_index separator ',') as value "
                        + "from information_schema.statistics "
                        + "where table_schema = database() and index_name in ("
                        + placeholders + ") group by index_name",
                "index_name",
                EXPECTED_INDEXES.toArray()
        );
    }

    private Map<String, String> queryNameValueMap(
            String sql,
            String nameColumn,
            Object... arguments) {
        return jdbcTemplate.query(sql, resultSet -> {
            Map<String, String> result = new java.util.HashMap<>();
            while (resultSet.next()) {
                result.put(
                        resultSet.getString(nameColumn),
                        resultSet.getString("value")
                );
            }
            return result;
        }, arguments);
    }

    private void assertImportantColumn(
            String tableName,
            String columnName,
            String dataType,
            boolean nullable,
            String defaultValue) {
        Map<String, Object> column = jdbcTemplate.queryForMap("""
                select data_type, is_nullable, column_default
                from information_schema.columns
                where table_schema = database()
                  and table_name = ?
                  and column_name = ?
                """, tableName, columnName);
        assertThat(column.get("data_type")).isEqualTo(dataType);
        assertThat(column.get("is_nullable"))
                .isEqualTo(nullable ? "YES" : "NO");
        assertThat(column.get("column_default")).isEqualTo(defaultValue);
    }

    private long count(String tableName) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + tableName,
                Long.class
        );
    }
}
