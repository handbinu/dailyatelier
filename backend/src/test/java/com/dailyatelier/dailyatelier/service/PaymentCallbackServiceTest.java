package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.config.TimeConfig;
import com.dailyatelier.dailyatelier.entity.PaymentCallbackEvent;
import com.dailyatelier.dailyatelier.entity.PaymentCallbackStatus;
import com.dailyatelier.dailyatelier.entity.PaymentProvider;
import com.dailyatelier.dailyatelier.repository.PaymentCallbackEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:callback-inbox;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({PaymentCallbackService.class, TimeConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentCallbackServiceTest {
    @Autowired PaymentCallbackService service;
    @Autowired PaymentCallbackEventRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void duplicateEventReturnsExistingResultAndRunsProcessorOnce() {
        PaymentCallbackEvent first = service.receive(
                PaymentProvider.INTERNAL, "event-1", null, "hash");
        PaymentCallbackEvent duplicate = service.receive(
                PaymentProvider.INTERNAL, "event-1", null, "hash");
        assertThat(duplicate.getCallbackEventId()).isEqualTo(first.getCallbackEventId());

        AtomicInteger calls = new AtomicInteger();
        service.process(PaymentProvider.INTERNAL, "event-1", event -> calls.incrementAndGet());
        service.process(PaymentProvider.INTERNAL, "event-1", event -> calls.incrementAndGet());
        assertThat(calls).hasValue(1);
        assertThat(repository.findById(first.getCallbackEventId()).orElseThrow().getStatus())
                .isEqualTo(PaymentCallbackStatus.PROCESSED);
    }

    @Test
    void rejectsSameEventIdWithDifferentPayload() {
        service.receive(PaymentProvider.INTERNAL, "event-2", null, "hash-a");
        assertThatThrownBy(() -> service.receive(
                PaymentProvider.INTERNAL, "event-2", null, "hash-b"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void retriesFailedEventOnlyUntilMaximumAttemptCount() {
        service.receive(PaymentProvider.INTERNAL, "event-3", null, "hash");
        for (int attempt = 0; attempt < PaymentCallbackService.MAX_ATTEMPTS; attempt++) {
            service.process(PaymentProvider.INTERNAL, "event-3",
                    event -> { throw new IllegalStateException("temporary"); });
        }
        PaymentCallbackEvent failed = repository
                .findByProviderAndProviderEventId(PaymentProvider.INTERNAL, "event-3")
                .orElseThrow();
        assertThat(failed.getAttemptCount()).isEqualTo(PaymentCallbackService.MAX_ATTEMPTS);
        assertThat(failed.getStatus()).isEqualTo(PaymentCallbackStatus.FAILED);
        assertThatThrownBy(() -> service.process(
                PaymentProvider.INTERNAL, "event-3", event -> {}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("최대 재시도");
    }
}
