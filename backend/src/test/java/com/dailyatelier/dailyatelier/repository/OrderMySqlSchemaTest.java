package com.dailyatelier.dailyatelier.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(
        named = "DAILYATELIER_MYSQL_SCHEMA_TEST",
        matches = "true"
)
class OrderMySqlSchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ordersTableHasUniqueArtAndRequiredIndexes() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList("""
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
                  and table_name = 'orders'
                group by index_name, non_unique
                """);

        assertThat(indexes).anySatisfy(index -> {
            assertThat(index.get("columns_in_order")).isEqualTo("art_id");
            assertThat(((Number) index.get("non_unique")).intValue()).isZero();
        });
        assertThat(indexes).anySatisfy(index ->
                assertThat(index.get("columns_in_order"))
                        .isEqualTo("buyer_id,status,created_at"));
        assertThat(indexes).anySatisfy(index ->
                assertThat(index.get("columns_in_order"))
                        .isEqualTo("seller_id,status,created_at"));
        assertThat(indexes).anySatisfy(index ->
                assertThat(index.get("columns_in_order"))
                        .isEqualTo("status,payment_due_at,order_id"));

        Map<String, Object> zipCodeColumn = jdbcTemplate.queryForMap("""
                select data_type, character_maximum_length
                from information_schema.columns
                where table_schema = database()
                  and table_name = 'address'
                  and column_name = 'zip_code'
                """);
        assertThat(zipCodeColumn.get("data_type")).isEqualTo("varchar");
        assertThat(((Number) zipCodeColumn.get("character_maximum_length"))
                .intValue()).isEqualTo(5);
    }
}
