package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.Order;
import com.dailyatelier.dailyatelier.entity.OrderRefundRequestStatus;
import com.dailyatelier.dailyatelier.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class OrderSummaryResponseDto {
    private Long orderId;
    private String orderNumber;
    private Long artId;
    private String artName;
    private String artImage;
    private String counterpartyName;
    private Integer winningPrice;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime paymentDueAt;
    private boolean shippingAddressConfirmed;
    private String shippingCarrier;
    private String trackingNumber;
    private OrderRefundRequestStatus refundRequestStatus;
    private List<OrderAction> availableActions;

    public static OrderSummaryResponseDto forBuyer(Order order) {
        return from(
                order,
                order.getSellerArtistNameSnapshot(),
                OrderViewPolicy.buyerActions(order)
        );
    }

    public static OrderSummaryResponseDto forSeller(Order order) {
        return from(
                order,
                order.getBuyerNicknameSnapshot(),
                OrderViewPolicy.sellerActions(order)
        );
    }

    private static OrderSummaryResponseDto from(
            Order order,
            String counterpartyName,
            List<OrderAction> actions) {
        return new OrderSummaryResponseDto(
                order.getOrderId(),
                OrderViewPolicy.orderNumber(order.getOrderId()),
                order.getArtIdSnapshot(),
                order.getArtNameSnapshot(),
                order.getArtImageSnapshot(),
                counterpartyName,
                order.getWinningPrice(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getPaymentDueAt(),
                order.isShippingAddressConfirmed(),
                order.getShippingCarrier(),
                order.getTrackingNumber(),
                order.getRefundRequestStatus(),
                actions
        );
    }
}
