package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.Order;
import com.dailyatelier.dailyatelier.entity.OrderCancelReason;
import com.dailyatelier.dailyatelier.entity.OrderRefundReason;
import com.dailyatelier.dailyatelier.entity.OrderStatus;
import com.dailyatelier.dailyatelier.exception.OrderApiException;
import com.dailyatelier.dailyatelier.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OrderStateService implements OrderPaymentService {
    private final OrderRepository orderRepository;
    private final Clock clock;

    @Override
    @Transactional
    public Order markPaid(Long orderId) {
        Order order = findForUpdate(orderId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!now.isBefore(order.getPaymentDueAt())) {
            throw conflict(
                    "PAYMENT_DEADLINE_EXPIRED",
                    "결제 기한이 만료된 주문입니다."
            );
        }
        transition(order, OrderStatus.PAID, now, null);
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public Order refund(Long orderId, OrderRefundReason reason) {
        Order order = findForUpdate(orderId);
        OrderRefundReason requiredReason = Objects.requireNonNull(
                reason,
                "환불 사유는 필수입니다"
        );
        transition(
                order,
                OrderStatus.REFUNDED,
                LocalDateTime.now(clock),
                requiredReason.name()
        );
        return orderRepository.save(order);
    }

    @Transactional
    public Order startPreparing(Long orderId, String sellerId) {
        Order order = findForUpdate(orderId);
        verifySeller(order, sellerId);
        transition(
                order,
                OrderStatus.PREPARING,
                LocalDateTime.now(clock),
                null
        );
        return orderRepository.save(order);
    }

    @Transactional
    public Order ship(
            Long orderId,
            String sellerId,
            String carrier,
            String trackingNumber) {
        Order order = findForUpdate(orderId);
        verifySeller(order, sellerId);
        try {
            order.recordShipment(
                    carrier,
                    trackingNumber,
                    LocalDateTime.now(clock)
            );
        } catch (IllegalArgumentException exception) {
            throw new OrderApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SHIPPING_INFO",
                    exception.getMessage()
            );
        } catch (IllegalStateException exception) {
            throw conflict("ORDER_STATUS_CONFLICT", exception.getMessage());
        }
        return orderRepository.save(order);
    }

    @Transactional
    public Order markDelivered(Long orderId) {
        Order order = findForUpdate(orderId);
        transition(
                order,
                OrderStatus.DELIVERED,
                LocalDateTime.now(clock),
                null
        );
        return orderRepository.save(order);
    }

    @Transactional
    public Order confirm(Long orderId, String buyerId) {
        Order order = findForUpdate(orderId);
        verifyBuyer(order, buyerId);
        transition(
                order,
                OrderStatus.CONFIRMED,
                LocalDateTime.now(clock),
                null
        );
        return orderRepository.save(order);
    }

    @Transactional
    public Order cancelPending(Long orderId, String buyerId) {
        Order order = findForUpdate(orderId);
        verifyBuyer(order, buyerId);
        transition(
                order,
                OrderStatus.CANCELED,
                LocalDateTime.now(clock),
                OrderCancelReason.BUYER_FORFEIT.name()
        );
        return orderRepository.save(order);
    }

    private Order findForUpdate(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderApiException(
                        HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND",
                        "주문을 찾을 수 없습니다."
                ));
    }

    private void verifyBuyer(Order order, String buyerId) {
        if (!order.getBuyer().getUserId().equals(buyerId)) {
            throw new OrderApiException(
                    HttpStatus.FORBIDDEN,
                    "ORDER_ACCESS_DENIED",
                    "본인의 주문만 처리할 수 있습니다."
            );
        }
    }

    private void verifySeller(Order order, String sellerId) {
        if (!order.getSeller().getUserId().equals(sellerId)) {
            throw new OrderApiException(
                    HttpStatus.FORBIDDEN,
                    "ORDER_ACCESS_DENIED",
                    "본인의 판매 주문만 처리할 수 있습니다."
            );
        }
    }

    private void transition(
            Order order,
            OrderStatus nextStatus,
            LocalDateTime transitionedAt,
            String reason) {
        try {
            order.transitionTo(nextStatus, transitionedAt, reason);
        } catch (IllegalStateException exception) {
            throw conflict("ORDER_STATUS_CONFLICT", exception.getMessage());
        }
    }

    private OrderApiException conflict(String code, String message) {
        return new OrderApiException(HttpStatus.CONFLICT, code, message);
    }
}
