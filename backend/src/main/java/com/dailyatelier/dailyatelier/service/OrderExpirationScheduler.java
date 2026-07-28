package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.OrderStatus;
import com.dailyatelier.dailyatelier.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class OrderExpirationScheduler {
    private final OrderExpirationService expirationService;
    private final OrderRepository orderRepository;
    private final Clock clock;
    private final int batchSize;

    public OrderExpirationScheduler(
            OrderExpirationService expirationService,
            OrderRepository orderRepository,
            Clock clock,
            @Value("${order.payment-expiration.batch-size:100}") int batchSize) {
        this.expirationService = expirationService;
        this.orderRepository = orderRepository;
        this.clock = clock;
        this.batchSize = Math.max(batchSize, 1);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void expireOrdersOnStartup() {
        expireOrders();
    }

    @Scheduled(
            fixedRateString = "${order.payment-expiration.interval-ms:60000}",
            initialDelayString = "${order.payment-expiration.interval-ms:60000}"
    )
    public void expireOrders() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> orderIds = orderRepository.findPaymentExpiredOrderIds(
                OrderStatus.PAYMENT_PENDING,
                now,
                PageRequest.of(0, batchSize)
        );

        int expiredCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (Long orderId : orderIds) {
            try {
                OrderExpirationResult result =
                        expirationService.expireOrder(orderId);
                switch (result) {
                    case EXPIRED -> expiredCount++;
                    case NOT_DUE, ALREADY_PROCESSED -> skippedCount++;
                }
            } catch (RuntimeException exception) {
                failedCount++;
                log.error("주문 결제 기한 만료 처리 실패: orderId={}", orderId, exception);
            }
        }

        if (!orderIds.isEmpty() || failedCount > 0) {
            log.info(
                    "주문 결제 기한 만료 배치 완료: targetCount={}, "
                            + "expiredCount={}, skippedCount={}, failedCount={}",
                    orderIds.size(),
                    expiredCount,
                    skippedCount,
                    failedCount
            );
        }
    }
}
