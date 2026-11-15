package com.dailyatelier.dailyatelier.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CloudinaryImageCleanupMigrationTest {
    private static final String MIGRATION =
            "db/migration/V8__add_cloudinary_image_cleanup_contract.sql";

    @Test
    void addsNullableIdentifiersAndLifecycleCleanupTableWithoutRewritingData()
            throws IOException {
        String sql;
        try (var input = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .toLowerCase();
        }

        assertThat(sql)
                .doesNotContain("delete from", "truncate table", "drop table")
                .contains(
                        "cloudinary_public_id varchar(320)",
                        "profile_image_public_id varchar(320)",
                        "create table cloudinary_cleanup",
                        "status varchar(20) not null",
                        "public_id varchar(320)",
                        "collate utf8mb4_bin",
                        "active_public_id varchar(320)",
                        "generated always as",
                        "when status in ('pending', 'processing') then public_id",
                        "unique (active_public_id)",
                        "status in ('pending', 'processing', 'done', 'failed')",
                        "attempt_count >= 0"
                )
                .doesNotContain("unique (public_id)");
    }
}
