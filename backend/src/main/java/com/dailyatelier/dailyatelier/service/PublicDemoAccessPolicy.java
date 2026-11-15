package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.exception.DomainApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PublicDemoAccessPolicy {
    private final boolean enabled;
    private final Set<String> allowedArtistIds;
    private final int signatureMaxAttempts;
    private final long signatureWindowSeconds;
    private final Clock clock;
    private final Map<String, Deque<Instant>> signatureAttemptsByUser = new HashMap<>();

    public PublicDemoAccessPolicy(
            @Value("${dailyatelier.public-demo.enabled:false}") boolean enabled,
            @Value("${dailyatelier.public-demo.allowed-artist-ids:}") String allowedArtistIds,
            @Value("${dailyatelier.public-demo.signature-max-attempts:5}") int signatureMaxAttempts,
            @Value("${dailyatelier.public-demo.signature-window-seconds:600}") long signatureWindowSeconds,
            Clock clock) {
        if (signatureMaxAttempts < 1 || signatureWindowSeconds < 1) {
            throw new IllegalArgumentException("Public demo signature limits must be positive");
        }
        this.enabled = enabled;
        this.allowedArtistIds = Arrays.stream(allowedArtistIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.signatureMaxAttempts = signatureMaxAttempts;
        this.signatureWindowSeconds = signatureWindowSeconds;
        this.clock = clock;
    }

    public void assertArtistRegistrationAllowed() {
        if (enabled) {
            throw new DomainApiException(
                    HttpStatus.FORBIDDEN,
                    "PUBLIC_DEMO_ARTIST_REGISTRATION_DISABLED",
                    "공개 데모에서는 새 작가 가입을 지원하지 않습니다"
            );
        }
    }

    public synchronized void assertCloudinarySignatureAllowed(String userId) {
        if (!enabled) return;
        if (!allowedArtistIds.contains(userId)) {
            throw new DomainApiException(
                    HttpStatus.FORBIDDEN,
                    "PUBLIC_DEMO_UPLOAD_FORBIDDEN",
                    "공개 데모 업로드 권한이 없습니다"
            );
        }

        Instant now = clock.instant();
        Instant cutoff = now.minusSeconds(signatureWindowSeconds);
        Deque<Instant> attempts = signatureAttemptsByUser.computeIfAbsent(
                userId, ignored -> new ArrayDeque<>()
        );
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
            attempts.removeFirst();
        }
        if (attempts.size() >= signatureMaxAttempts) {
            throw new DomainApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "PUBLIC_DEMO_UPLOAD_RATE_LIMITED",
                    "잠시 후 다시 업로드를 시도해 주세요"
            );
        }
        attempts.addLast(now);
    }
}
