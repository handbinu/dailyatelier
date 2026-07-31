package com.dailyatelier.dailyatelier.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "payment_callback_event", uniqueConstraints = {
        @UniqueConstraint(name = "uq_callback_provider_event", columnNames = {"provider", "provider_event_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCallbackEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "callback_event_id")
    private Long callbackEventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentProvider provider;

    @Column(name = "provider_event_id", nullable = false, length = 100)
    private String providerEventId;

    @Column(name = "pg_order_id", length = 100)
    private String pgOrderId;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentCallbackStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public static PaymentCallbackEvent receive(PaymentProvider provider, String eventId,
                                                String pgOrderId, String payloadHash,
                                                LocalDateTime receivedAt) {
        PaymentCallbackEvent event = new PaymentCallbackEvent();
        event.provider = Objects.requireNonNull(provider);
        event.providerEventId = requireText(eventId);
        event.pgOrderId = pgOrderId;
        event.payloadHash = requireText(payloadHash);
        event.status = PaymentCallbackStatus.RECEIVED;
        event.receivedAt = Objects.requireNonNull(receivedAt);
        return event;
    }

    public boolean samePayload(String payloadHash) {
        return this.payloadHash.equals(payloadHash);
    }

    public void processed(LocalDateTime now) {
        if (status == PaymentCallbackStatus.PROCESSED) return;
        attemptCount++;
        status = PaymentCallbackStatus.PROCESSED;
        processedAt = Objects.requireNonNull(now);
        lastError = null;
    }

    public void failed(String error) {
        if (status == PaymentCallbackStatus.PROCESSED) {
            throw new IllegalStateException("처리 완료된 콜백은 실패로 변경할 수 없습니다");
        }
        attemptCount++;
        status = PaymentCallbackStatus.FAILED;
        lastError = error;
    }

    public boolean retryable(int maxAttempts) {
        return status == PaymentCallbackStatus.FAILED && attemptCount < maxAttempts;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("필수 문자열이 비어 있습니다");
        return value;
    }
}
