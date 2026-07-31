package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.PaymentCallbackEvent;
import com.dailyatelier.dailyatelier.entity.PaymentCallbackStatus;
import com.dailyatelier.dailyatelier.entity.PaymentProvider;
import com.dailyatelier.dailyatelier.payment.PaymentCallbackProcessor;
import com.dailyatelier.dailyatelier.repository.PaymentCallbackEventRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentCallbackService {
    public static final int MAX_ATTEMPTS = 5;

    private final PaymentCallbackEventRepository eventRepository;
    private final Clock clock;

    @Transactional
    public PaymentCallbackEvent receive(PaymentProvider provider, String eventId,
                                        String pgOrderId, String payloadHash) {
        PaymentCallbackEvent existing = eventRepository
                .findByProviderAndProviderEventId(provider, eventId)
                .orElse(null);
        if (existing != null) {
            if (!existing.samePayload(payloadHash)) {
                throw new IllegalStateException("같은 콜백 이벤트 ID의 원문이 일치하지 않습니다");
            }
            return existing;
        }
        return eventRepository.save(PaymentCallbackEvent.receive(
                provider, eventId, pgOrderId, payloadHash, LocalDateTime.now(clock)));
    }

    @Transactional
    public PaymentCallbackEvent process(PaymentProvider provider, String eventId,
                                        PaymentCallbackProcessor processor) {
        PaymentCallbackEvent event = eventRepository.findForUpdate(provider, eventId)
                .orElseThrow(() -> new IllegalArgumentException("콜백 이벤트를 찾을 수 없습니다"));
        if (event.getStatus() == PaymentCallbackStatus.PROCESSED) return event;
        if (event.getStatus() == PaymentCallbackStatus.FAILED
                && !event.retryable(MAX_ATTEMPTS)) {
            throw new IllegalStateException("콜백 최대 재시도 횟수를 초과했습니다");
        }
        try {
            processor.process(event);
            event.processed(LocalDateTime.now(clock));
        } catch (RuntimeException exception) {
            event.failed(abbreviate(exception.getMessage()));
        }
        return event;
    }

    private String abbreviate(String value) {
        if (value == null) return null;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
