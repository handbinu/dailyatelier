package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.exception.DomainApiException;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.PublicDemoArtType;
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
    private final String publicArtistId;
    private final String publicBuyerId;
    private final int signatureMaxAttempts;
    private final long signatureWindowSeconds;
    private final Clock clock;
    private final Map<String, Deque<Instant>> signatureAttemptsByUser = new HashMap<>();

    public PublicDemoAccessPolicy(
            @Value("${dailyatelier.public-demo.enabled:false}") boolean enabled,
            @Value("${dailyatelier.public-demo.allowed-artist-ids:}") String allowedArtistIds,
            @Value("${dailyatelier.public-demo.artist-id:demo-artist-aria}") String publicArtistId,
            @Value("${dailyatelier.public-demo.buyer-id:demo-buyer-bid-qa}") String publicBuyerId,
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
        this.publicArtistId = publicArtistId;
        this.publicBuyerId = publicBuyerId;
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
        if (publicArtistId.equals(userId)) {
            throw forbidden("PUBLIC_DEMO_ARTIST_PROXY_UPLOAD_REQUIRED", "공개 데모 작가는 서버 업로드만 사용할 수 있습니다");
        }
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

    public synchronized void assertPublicArtistUploadAllowed(String userId) {
        if (!enabled) return;
        if (!publicArtistId.equals(userId)) {
            throw forbidden("PUBLIC_DEMO_ARTIST_PROXY_UPLOAD_FORBIDDEN", "공개 데모 작가만 서버 업로드를 사용할 수 있습니다");
        }
        assertRateLimit(userId);
    }

    public void assertAccountMutationAllowed(String userId) {
        if (enabled && (publicArtistId.equals(userId) || publicBuyerId.equals(userId))) {
            throw forbidden("PUBLIC_DEMO_ACCOUNT_MUTATION_FORBIDDEN", "공개 데모 계정 정보는 변경할 수 없습니다");
        }
    }

    public void assertPublicDemoContentMutationAllowed(String userId) {
        if (enabled && (publicArtistId.equals(userId) || publicBuyerId.equals(userId))) {
            throw forbidden("PUBLIC_DEMO_CONTENT_MUTATION_FORBIDDEN", "공개 데모 계정에서는 이 변경을 지원하지 않습니다");
        }
    }

    public void assertArtMutationAllowed(String userId, Art art) {
        if (!enabled || !publicArtistId.equals(userId)) return;
        if (art.getPublicDemoType() != PublicDemoArtType.ARTIST_CREATED) {
            throw forbidden("PUBLIC_DEMO_ART_PROTECTED", "기존 전시 및 관리형 데모 작품은 변경할 수 없습니다");
        }
    }

    public void assertPublicBuyerBidAllowed(String userId, Art art) {
        if (!enabled || !publicBuyerId.equals(userId)) return;
        boolean ownedByPublicArtist = art.getArtist() != null && art.getArtist().getUser() != null
                && publicArtistId.equals(art.getArtist().getUser().getUserId());
        if (!ownedByPublicArtist || (art.getPublicDemoType() != PublicDemoArtType.MANAGED
                && art.getPublicDemoType() != PublicDemoArtType.ARTIST_CREATED)) {
            throw forbidden("PUBLIC_DEMO_BID_FORBIDDEN", "공개 데모 구매자는 체험 작품에만 입찰할 수 있습니다");
        }
    }

    public void assertPublicBuyerOrderMutationAllowed(String userId, Art art, boolean cancel) {
        if (!enabled || !publicBuyerId.equals(userId)) return;
        if (cancel && art.getPublicDemoType() == PublicDemoArtType.MANAGED) return;
        throw forbidden("PUBLIC_DEMO_ORDER_MUTATION_FORBIDDEN", "공개 데모에서는 이 주문 변경을 지원하지 않습니다");
    }

    public boolean requiresPublicBuyerOrderRestrictions(String userId) {
        return enabled && publicBuyerId.equals(userId);
    }

    public void assertPublicArtistOrderMutationAllowed(String userId) {
        if (enabled && publicArtistId.equals(userId)) {
            throw forbidden("PUBLIC_DEMO_ORDER_MUTATION_FORBIDDEN", "공개 데모에서는 판매 주문을 변경할 수 없습니다");
        }
    }

    public boolean isPublicArtist(String userId) {
        return enabled && publicArtistId.equals(userId);
    }

    private void assertRateLimit(String userId) {
        Instant now = clock.instant();
        Instant cutoff = now.minusSeconds(signatureWindowSeconds);
        Deque<Instant> attempts = signatureAttemptsByUser.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) attempts.removeFirst();
        if (attempts.size() >= signatureMaxAttempts) {
            throw new DomainApiException(HttpStatus.TOO_MANY_REQUESTS, "PUBLIC_DEMO_UPLOAD_RATE_LIMITED", "잠시 후 다시 업로드를 시도해 주세요");
        }
        attempts.addLast(now);
    }

    private DomainApiException forbidden(String code, String message) {
        return new DomainApiException(HttpStatus.FORBIDDEN, code, message);
    }
}
