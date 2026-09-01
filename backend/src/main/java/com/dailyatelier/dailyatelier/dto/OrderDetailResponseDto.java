package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.Order;
import com.dailyatelier.dailyatelier.entity.OrderStatus;
import com.dailyatelier.dailyatelier.entity.OrderRefundRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class OrderDetailResponseDto {
    private Long orderId;
    private Long reviewId;
    private String orderNumber;
    private Long artId;
    private String artName;
    private String artImage;
    private Integer winningPrice;
    private OrderStatus status;
    private String buyerName;
    private String buyerNickname;
    private String buyerPhone;
    private String sellerName;
    private String sellerNickname;
    private String sellerArtistName;
    private OrderShippingSnapshotDto shippingAddress;
    private String shippingCarrier;
    private String trackingNumber;
    private LocalDateTime createdAt;
    private LocalDateTime paymentDueAt;
    private LocalDateTime addressConfirmedAt;
    private LocalDateTime paidAt;
    private LocalDateTime preparingAt;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime canceledAt;
    private LocalDateTime refundedAt;
    private String cancelReason;
    private String refundReason;
    private OrderRefundRequestStatus refundRequestStatus;
    private String refundRequestReason;
    private LocalDateTime refundRequestedAt;
    private LocalDateTime refundRejectedAt;
    private List<OrderAction> availableActions;

    public static OrderDetailResponseDto forBuyer(Order order) {
        return forBuyer(order, null);
    }

    public static OrderDetailResponseDto forBuyer(Order order, Long reviewId) {
        return from(order, reviewId, OrderViewPolicy.buyerActions(order));
    }

    public static OrderDetailResponseDto forSeller(Order order) {
        return from(order, null, OrderViewPolicy.sellerActions(order));
    }

    private static OrderDetailResponseDto from(
            Order order,
            Long reviewId,
            List<OrderAction> availableActions) {
        return new OrderDetailResponseDto(
                order.getOrderId(),
                reviewId,
                OrderViewPolicy.orderNumber(order.getOrderId()),
                order.getArtIdSnapshot(),
                order.getArtNameSnapshot(),
                order.getArtImageSnapshot(),
                order.getWinningPrice(),
                order.getStatus(),
                order.getBuyerNameSnapshot(),
                order.getBuyerNicknameSnapshot(),
                order.getBuyerPhoneSnapshot(),
                order.getSellerNameSnapshot(),
                order.getSellerNicknameSnapshot(),
                order.getSellerArtistNameSnapshot(),
                OrderShippingSnapshotDto.from(order.getShippingAddress()),
                order.getShippingCarrier(),
                order.getTrackingNumber(),
                order.getCreatedAt(),
                order.getPaymentDueAt(),
                order.getAddressConfirmedAt(),
                order.getPaidAt(),
                order.getPreparingAt(),
                order.getShippedAt(),
                order.getDeliveredAt(),
                order.getConfirmedAt(),
                order.getCanceledAt(),
                order.getRefundedAt(),
                order.getCancelReason(),
                order.getRefundReason(),
                order.getRefundRequestStatus(),
                order.getRefundRequestReason(),
                order.getRefundRequestedAt(),
                order.getRefundRejectedAt(),
                availableActions
        );
    }
}
