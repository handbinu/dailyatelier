package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.PaymentCallbackEvent;
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
    private final PaymentCallbackTransactionService transactionService;
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

    public PaymentCallbackEvent process(PaymentProvider provider, String eventId,
                                        PaymentCallbackProcessor processor) {
        try {
            return transactionService.processAttempt(provider, eventId, processor);
        } catch (PaymentCallbackProcessingException exception) {
            return transactionService.recordFailure(
                    provider, eventId, abbreviate(exception.getMessage()));
        }
    }

    private String abbreviate(String value) {
        if (value == null) return null;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
