package com.dailyatelier.dailyatelier.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudinaryCleanupPropertiesTest {

    @ParameterizedTest
    @MethodSource("invalidConfigurations")
    void rejectsInvalidConfiguration(
            long intervalMs,
            int batchSize,
            int maxAttempts,
            long initialBackoffMs,
            long maxBackoffMs,
            long leaseMs,
            int connectTimeoutMs,
            int readTimeoutMs) {
        assertThatThrownBy(() -> new CloudinaryCleanupProperties(
                intervalMs,
                batchSize,
                maxAttempts,
                initialBackoffMs,
                maxBackoffMs,
                leaseMs,
                connectTimeoutMs,
                readTimeoutMs
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid cloudinary.cleanup configuration");
    }

    private static Stream<Arguments> invalidConfigurations() {
        return Stream.of(
                Arguments.of(999L, 20, 5, 1_000L, 8_000L, 120_000L, 5_000, 30_000),
                Arguments.of(86_400_001L, 20, 5, 1_000L, 8_000L, 120_000L, 5_000, 30_000),
                Arguments.of(60_000L, 0, 5, 1_000L, 8_000L, 120_000L, 5_000, 30_000),
                Arguments.of(60_000L, 1_001, 5, 1_000L, 8_000L, 120_000L, 5_000, 30_000),
                Arguments.of(60_000L, 20, 0, 1_000L, 8_000L, 120_000L, 5_000, 30_000),
                Arguments.of(60_000L, 20, 101, 1_000L, 8_000L, 120_000L, 5_000, 30_000),
                Arguments.of(60_000L, 20, 5, 8_001L, 8_000L, 120_000L, 5_000, 30_000),
                Arguments.of(60_000L, 20, 5, 1_000L, 604_800_001L, 120_000L, 5_000, 30_000),
                Arguments.of(60_000L, 20, 5, 1_000L, 8_000L, 35_000L, 5_000, 30_000),
                Arguments.of(60_000L, 20, 5, 1_000L, 8_000L, 3_600_001L, 5_000, 30_000),
                Arguments.of(60_000L, 20, 5, 1_000L, 8_000L, 120_000L, 0, 30_000),
                Arguments.of(60_000L, 20, 5, 1_000L, 8_000L, 120_000L, 5_000, 300_001)
        );
    }
}
