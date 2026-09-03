package com.dailyatelier.dailyatelier.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileImageMigrationTest {
    private static final String MIGRATION =
            "db/migration/V7__add_user_profile_image_url.sql";

    @Test
    void addsNullableProfileImageUrlWithoutChangingExistingUsers() throws IOException {
        String sql;
        try (var input = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .toLowerCase();
        }

        assertThat(sql)
                .doesNotContain("delete from", "truncate table", "drop table")
                .contains("add column profile_image_url varchar(500) null");
    }
}
