package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.PaymentCallbackEvent;
import com.dailyatelier.dailyatelier.entity.PaymentProvider;
import com.dailyatelier.dailyatelier.repository.PaymentCallbackEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class PaymentCallbackEventCreationService {
    private final PaymentCallbackEventRepository eventRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentCallbackEvent create(PaymentProvider provider, String eventId,
                                       String pgOrderId, String payloadHash) {
        return eventRepository.saveAndFlush(PaymentCallbackEvent.receive(
                provider, eventId, pgOrderId, payloadHash, LocalDateTime.now(clock)));
    }
}
