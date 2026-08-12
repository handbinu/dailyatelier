package com.dailyatelier.dailyatelier.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MinimumBidIncrementMigrationTest {
    private static final String MIGRATION =
            "db/migration/V4__add_minimum_bid_increment.sql";

    @Test
    void addsBackfilledDefaultedAndConstrainedColumnWithoutDeletingArt() throws IOException {
        String sql;
        try (var input = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .toLowerCase();
        }

        assertThat(sql)
                .doesNotContain("delete from", "truncate table", "drop table")
                .contains(
                        "minimum_bid_increment int not null default 1000",
                        "minimum_bid_increment between 100 and 10000000",
                        "mod(minimum_bid_increment, 100) = 0"
                );
    }
}
