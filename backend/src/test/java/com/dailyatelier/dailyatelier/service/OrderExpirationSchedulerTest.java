package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.OrderStatus;
import com.dailyatelier.dailyatelier.repository.OrderRepository;
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
        when(orderRepository.findPaymentExpiredOrderIds(
                eq(OrderStatus.PAYMENT_PENDING),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(1L, 2L, 3L, 4L));
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
        when(orderRepository.findPaymentExpiredOrderIds(
                eq(OrderStatus.PAYMENT_PENDING),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of());

        scheduler.expireOrdersOnStartup();

        verify(orderRepository).findPaymentExpiredOrderIds(
                eq(OrderStatus.PAYMENT_PENDING),
                nowCaptor.capture(),
                pageableCaptor.capture()
        );
        assertThat(nowCaptor.getValue())
                .isEqualTo(LocalDateTime.of(2026, 7, 29, 18, 0));
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void failedOrderIsRetriedOnNextCycle() {
        when(orderRepository.findPaymentExpiredOrderIds(
                eq(OrderStatus.PAYMENT_PENDING),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(7L));
        when(expirationService.expireOrder(7L))
                .thenThrow(new IllegalStateException("temporary failure"));

        scheduler.expireOrders();
        scheduler.expireOrders();

        verify(expirationService, times(2)).expireOrder(7L);
    }
}
