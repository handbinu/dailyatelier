package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.PaymentCallbackEvent;
import com.dailyatelier.dailyatelier.entity.PaymentProvider;
import com.dailyatelier.dailyatelier.payment.PaymentCallbackProcessor;
import com.dailyatelier.dailyatelier.repository.PaymentCallbackEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;

@Service
@RequiredArgsConstructor
public class PaymentCallbackService {
    public static final int MAX_ATTEMPTS = 5;

    private final PaymentCallbackEventRepository eventRepository;
    private final PaymentCallbackEventCreationService eventCreationService;
    private final PaymentCallbackReceiveObserver receiveObserver;
    private final PaymentCallbackTransactionService transactionService;

    public PaymentCallbackEvent receive(PaymentProvider provider, String eventId,
                                        String pgOrderId, String payloadHash) {
        PaymentCallbackEvent existing = findExisting(provider, eventId);
        if (existing != null) {
            assertSameReceipt(existing, pgOrderId, payloadHash);
            return existing;
        }

        receiveObserver.afterFirstLookupMiss();
        try {
            return eventCreationService.create(provider, eventId, pgOrderId, payloadHash);
        } catch (DataIntegrityViolationException exception) {
            if (!isCallbackEventDuplicate(exception)) throw exception;

            PaymentCallbackEvent concurrentEvent = findExisting(provider, eventId);
            if (concurrentEvent == null) throw exception;
            assertSameReceipt(concurrentEvent, pgOrderId, payloadHash);
            return concurrentEvent;
        }
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

    private PaymentCallbackEvent findExisting(PaymentProvider provider, String eventId) {
        return eventRepository.findByProviderAndProviderEventId(provider, eventId).orElse(null);
    }

    private void assertSameReceipt(PaymentCallbackEvent event, String pgOrderId, String payloadHash) {
        if (!event.sameReceipt(pgOrderId, payloadHash)) {
            throw new IllegalStateException("같은 콜백 이벤트 ID의 원문이 일치하지 않습니다");
        }
    }

    private boolean isCallbackEventDuplicate(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException sqlException
                    && sqlException.getErrorCode() == 1062
                    && sqlException.getMessage().contains("uq_callback_provider_event")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
