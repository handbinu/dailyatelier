package com.dailyatelier.dailyatelier.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CloudinaryCleanupProperties {
    private static final long MIN_INTERVAL_MS = 1_000L;
    private static final long MAX_INTERVAL_MS = 86_400_000L;
    private static final int MAX_BATCH_SIZE = 1_000;
    private static final int MAX_ATTEMPTS_LIMIT = 100;
    private static final long MAX_BACKOFF_LIMIT_MS = 604_800_000L;
    private static final long MAX_LEASE_MS = 3_600_000L;
    private static final int MAX_TIMEOUT_MS = 300_000;

    private final long intervalMs;
    private final int batchSize;
    private final int maxAttempts;
    private final long initialBackoffMs;
    private final long maxBackoffMs;
    private final long leaseMs;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public CloudinaryCleanupProperties(
            @Value("${cloudinary.cleanup.interval-ms:60000}") long intervalMs,
            @Value("${cloudinary.cleanup.batch-size:20}") int batchSize,
            @Value("${cloudinary.cleanup.max-attempts:5}") int maxAttempts,
            @Value("${cloudinary.cleanup.initial-backoff-ms:60000}") long initialBackoffMs,
            @Value("${cloudinary.cleanup.max-backoff-ms:3600000}") long maxBackoffMs,
            @Value("${cloudinary.cleanup.lease-ms:120000}") long leaseMs,
            @Value("${cloudinary.cleanup.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${cloudinary.cleanup.read-timeout-ms:30000}") int readTimeoutMs) {
        if (intervalMs < MIN_INTERVAL_MS || intervalMs > MAX_INTERVAL_MS) {
            throw invalid("interval-ms must be between " + MIN_INTERVAL_MS
                    + " and " + MAX_INTERVAL_MS);
        }
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw invalid("batch-size must be between 1 and " + MAX_BATCH_SIZE);
        }
        if (maxAttempts < 1 || maxAttempts > MAX_ATTEMPTS_LIMIT) {
            throw invalid("max-attempts must be between 1 and " + MAX_ATTEMPTS_LIMIT);
        }
        if (initialBackoffMs < 1
                || maxBackoffMs < initialBackoffMs
                || maxBackoffMs > MAX_BACKOFF_LIMIT_MS) {
            throw invalid("backoff values must be positive, ordered, and at most "
                    + MAX_BACKOFF_LIMIT_MS);
        }
        if (connectTimeoutMs < 1 || connectTimeoutMs > MAX_TIMEOUT_MS
                || readTimeoutMs < 1 || readTimeoutMs > MAX_TIMEOUT_MS) {
            throw invalid("Cloudinary timeouts must be between 1 and " + MAX_TIMEOUT_MS);
        }
        if (leaseMs > MAX_LEASE_MS
                || leaseMs <= (long) connectTimeoutMs + readTimeoutMs) {
            throw invalid("lease-ms must exceed the combined Cloudinary timeouts and be at most "
                    + MAX_LEASE_MS);
        }
        this.intervalMs = intervalMs;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.initialBackoffMs = initialBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
        this.leaseMs = leaseMs;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getInitialBackoffMs() {
        return initialBackoffMs;
    }

    public long getMaxBackoffMs() {
        return maxBackoffMs;
    }

    public long getLeaseMs() {
        return leaseMs;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    private IllegalStateException invalid(String message) {
        return new IllegalStateException("Invalid cloudinary.cleanup configuration: " + message);
    }
}
