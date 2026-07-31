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
    private final OrderPointLedgerService pointLedgerService;
    private final Clock clock;

    @Transactional
    public Order markPaid(Long orderId) {
        return markPaid(orderId, "order-payment:" + orderId);
    }

    @Override
    @Transactional
    public Order markPaid(Long orderId, String idempotencyKey) {
        return markPaid(orderId, null, idempotencyKey);
    }

    @Transactional
    public Order markPaid(Long orderId, String buyerId, String idempotencyKey) {
        Order order = findForUpdate(orderId);
        if (buyerId != null) {
            verifyBuyer(order, buyerId);
        }
        if (order.getStatus() == OrderStatus.PAID) {
            return order;
        }
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            throw conflict("ORDER_STATUS_CONFLICT", "결제 대기 주문만 결제할 수 있습니다.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!now.isBefore(order.getPaymentDueAt())) {
            throw conflict(
                    "PAYMENT_DEADLINE_EXPIRED",
                    "결제 기한이 만료된 주문입니다."
            );
        }
        if (!order.isShippingAddressConfirmed()) {
            throw conflict(
                    "SHIPPING_ADDRESS_REQUIRED",
                    "결제 전에 배송지를 확정해 주세요."
            );
        }
        pointLedgerService.commit(order, idempotencyKey, now);
        transition(order, OrderStatus.PAID, now, null);
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public Order refund(Long orderId, OrderRefundReason reason, String idempotencyKey) {
        Order order = findForUpdate(orderId);
        if (order.getStatus() == OrderStatus.REFUNDED) {
            return order;
        }
        if (order.getStatus() != OrderStatus.PAID
                && order.getStatus() != OrderStatus.PREPARING) {
            throw conflict("ORDER_STATUS_CONFLICT", "결제된 주문만 환불할 수 있습니다.");
        }
        OrderRefundReason requiredReason = Objects.requireNonNull(
                reason,
                "환불 사유는 필수입니다"
        );
        pointLedgerService.refund(order, idempotencyKey, requiredReason, LocalDateTime.now(clock));
        transition(
                order,
                OrderStatus.REFUNDED,
                LocalDateTime.now(clock),
                requiredReason.name()
        );
        return orderRepository.save(order);
    }

    @Transactional
    public Order refund(Long orderId, OrderRefundReason reason) {
        return refund(orderId, reason, "order-refund:" + orderId);
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
        LocalDateTime now = LocalDateTime.now(clock);
        OrderCancelReason reason = now.isBefore(order.getPaymentDueAt())
                ? OrderCancelReason.BUYER_FORFEIT
                : OrderCancelReason.PAYMENT_DEADLINE_EXPIRED;
        pointLedgerService.release(
                order,
                reason == OrderCancelReason.BUYER_FORFEIT
                        ? com.dailyatelier.dailyatelier.entity.PointHoldReleaseReason.ORDER_CANCELED
                        : com.dailyatelier.dailyatelier.entity.PointHoldReleaseReason.PAYMENT_EXPIRED,
                "order-release:" + orderId,
                now);
        transition(
                order,
                OrderStatus.CANCELED,
                now,
                reason.name()
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
