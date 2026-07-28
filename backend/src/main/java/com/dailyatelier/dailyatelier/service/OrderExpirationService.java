package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.Order;
import com.dailyatelier.dailyatelier.entity.OrderCancelReason;
import com.dailyatelier.dailyatelier.entity.OrderStatus;
import com.dailyatelier.dailyatelier.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrderExpirationService {
    private final OrderRepository orderRepository;
    private final Clock clock;

    @Transactional
    public OrderExpirationResult expireOrder(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "주문을 찾을 수 없습니다. orderId=" + orderId
                ));

        if (order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            return OrderExpirationResult.ALREADY_PROCESSED;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(order.getPaymentDueAt())) {
            return OrderExpirationResult.NOT_DUE;
        }

        order.transitionTo(
                OrderStatus.CANCELED,
                now,
                OrderCancelReason.PAYMENT_DEADLINE_EXPIRED.name()
        );
        orderRepository.save(order);
        return OrderExpirationResult.EXPIRED;
    }
}
