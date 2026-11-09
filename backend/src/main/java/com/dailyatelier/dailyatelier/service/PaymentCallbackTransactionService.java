package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.PaymentCallbackEvent;
import com.dailyatelier.dailyatelier.entity.PaymentCallbackStatus;
import com.dailyatelier.dailyatelier.entity.PaymentProvider;
import com.dailyatelier.dailyatelier.payment.PaymentCallbackProcessor;
import com.dailyatelier.dailyatelier.repository.PaymentCallbackEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class PaymentCallbackTransactionService {
    private final PaymentCallbackEventRepository eventRepository;
    private final Clock clock;

    @Transactional
    public PaymentCallbackEvent processAttempt(PaymentProvider provider, String eventId,
                                               PaymentCallbackProcessor processor) {
        PaymentCallbackEvent event = findProcessableEvent(provider, eventId);
        if (event.getStatus() == PaymentCallbackStatus.PROCESSED) return event;

        try {
            processor.process(event);
        } catch (RuntimeException exception) {
            throw new PaymentCallbackProcessingException(exception);
        }
        event.processed(LocalDateTime.now(clock));
        return event;
    }

    @Transactional
    public PaymentCallbackEvent recordFailure(PaymentProvider provider, String eventId,
                                              String error) {
        PaymentCallbackEvent event = findProcessableEvent(provider, eventId);
        event.failed(error);
        return event;
    }

    private PaymentCallbackEvent findProcessableEvent(PaymentProvider provider, String eventId) {
        PaymentCallbackEvent event = eventRepository.findForUpdate(provider, eventId)
                .orElseThrow(() -> new IllegalArgumentException("콜백 이벤트를 찾을 수 없습니다"));
        if (event.getStatus() == PaymentCallbackStatus.FAILED
                && !event.retryable(PaymentCallbackService.MAX_ATTEMPTS)) {
            throw new IllegalStateException("콜백 최대 재시도 횟수를 초과했습니다");
        }
        return event;
    }
}

class PaymentCallbackProcessingException extends RuntimeException {
    PaymentCallbackProcessingException(RuntimeException cause) {
        super(cause.getMessage(), cause);
    }
}
