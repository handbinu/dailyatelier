package com.dailyatelier.dailyatelier.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ArtClassificationMigrationTest {
    private static final String MIGRATION =
            "db/migration/V3__add_art_classification_and_created_at.sql";

    @Test
    void changesOnlySchemaWithoutDeletingExistingArt() throws IOException {
        String sql;
        try (var input = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .toLowerCase();
        }

        assertThat(sql)
                .doesNotContain("delete from", "truncate table")
                .contains(
                        "add column format varchar(20) not null",
                        "add column category varchar(30) not null",
                        "add column created_at datetime(6) not null",
                        "add index idx_art_public_search",
                        "add index idx_art_created",
                        "add index idx_art_current_price"
                );
    }
}
