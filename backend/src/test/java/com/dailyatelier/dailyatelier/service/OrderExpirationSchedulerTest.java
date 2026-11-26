package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.OrderStatus;
import com.dailyatelier.dailyatelier.repository.OrderRepository;
import com.dailyatelier.dailyatelier.repository.DueBatchCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class OrderExpirationSchedulerTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-29T09:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Mock
    private OrderExpirationService expirationService;

    @Mock
    private OrderRepository orderRepository;

    private OrderExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OrderExpirationScheduler(
                expirationService,
                orderRepository,
                CLOCK,
                5
        );
    }

    @Test
    void isolatesFailuresAndLogsBatchSummary(CapturedOutput output) {
        when(orderRepository.findPaymentExpiredOrderBatch(
                eq(OrderStatus.PAYMENT_PENDING),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                any(Pageable.class)
        )).thenReturn(List.of(candidate(1L), candidate(2L), candidate(3L), candidate(4L)));
        when(expirationService.expireOrder(1L))
                .thenReturn(OrderExpirationResult.EXPIRED);
        when(expirationService.expireOrder(2L))
                .thenReturn(OrderExpirationResult.ALREADY_PROCESSED);
        when(expirationService.expireOrder(3L))
                .thenReturn(OrderExpirationResult.NOT_DUE);
        when(expirationService.expireOrder(4L))
                .thenThrow(new IllegalStateException("forced failure"));

        scheduler.expireOrders();

        verify(expirationService).expireOrder(4L);
        assertThat(output)
                .contains("주문 결제 기한 만료 처리 실패: orderId=4")
                .contains("targetCount=4")
                .contains("expiredCount=1")
                .contains("skippedCount=2")
                .contains("failedCount=1");
    }

    @Test
    void startupCatchUpUsesCurrentTimeAndConfiguredBatchSize() {
        ArgumentCaptor<LocalDateTime> nowCaptor =
                ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);
        when(orderRepository.findPaymentExpiredOrderBatch(
                eq(OrderStatus.PAYMENT_PENDING),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                any(Pageable.class)
        )).thenReturn(List.of());

        scheduler.expireOrdersOnStartup();

        verify(orderRepository).findPaymentExpiredOrderBatch(
                eq(OrderStatus.PAYMENT_PENDING),
                nowCaptor.capture(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                pageableCaptor.capture()
        );
        assertThat(nowCaptor.getValue())
                .isEqualTo(LocalDateTime.of(2026, 7, 29, 18, 0));
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void failedOrderIsRetriedOnNextCycle() {
        when(orderRepository.findPaymentExpiredOrderBatch(
                eq(OrderStatus.PAYMENT_PENDING),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                any(Pageable.class)
        )).thenReturn(List.of(candidate(7L)));
        when(expirationService.expireOrder(7L))
                .thenThrow(new IllegalStateException("temporary failure"));

        scheduler.expireOrders();
        scheduler.expireOrders();

        verify(expirationService, times(2)).expireOrder(7L);
    }

    @Test
    void advancesPastFailedFullBatchAndRestartsAfterTail() {
        LocalDateTime cutoff = LocalDateTime.now(CLOCK);
        var head = candidate(7L);
        var tail = candidate(8L);
        var limit = org.springframework.data.domain.PageRequest.of(0, 1);
        var onePerRun = new OrderExpirationScheduler(expirationService, orderRepository, CLOCK, 1);
        when(orderRepository.findPaymentExpiredOrderBatch(OrderStatus.PAYMENT_PENDING, cutoff, null, null, limit))
                .thenReturn(List.of(head));
        when(orderRepository.findPaymentExpiredOrderBatch(OrderStatus.PAYMENT_PENDING, cutoff, cutoff, 7L, limit))
                .thenReturn(List.of(tail));
        when(orderRepository.findPaymentExpiredOrderBatch(OrderStatus.PAYMENT_PENDING, cutoff, cutoff, 8L, limit))
                .thenReturn(List.of());
        when(expirationService.expireOrder(7L))
                .thenThrow(new IllegalStateException("fixture failure"))
                .thenReturn(OrderExpirationResult.EXPIRED);
        when(expirationService.expireOrder(8L)).thenReturn(OrderExpirationResult.EXPIRED);

        onePerRun.expireOrders();
        onePerRun.expireOrders();
        onePerRun.expireOrders();
        onePerRun.expireOrders();

        var sequence = org.mockito.Mockito.inOrder(expirationService);
        sequence.verify(expirationService).expireOrder(7L);
        sequence.verify(expirationService).expireOrder(8L);
        sequence.verify(expirationService).expireOrder(7L);
        sequence.verifyNoMoreInteractions();
    }

    private static DueBatchCandidate candidate(long id) {
        return new TestCandidate(id, LocalDateTime.now(CLOCK));
    }

    private record TestCandidate(Long id, LocalDateTime deadline) implements DueBatchCandidate {
        @Override public Long getId() { return id; }
        @Override public LocalDateTime getDeadline() { return deadline; }
    }
}
