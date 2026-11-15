package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.Order;
import com.dailyatelier.dailyatelier.entity.OrderShippingAddress;
import com.dailyatelier.dailyatelier.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class OrderShippingAddressResponseDto {
    private Long orderId;
    private OrderStatus status;
    private String recipientName;
    private String recipientPhone;
    private String zipCode;
    private String address1;
    private String address2;
    private LocalDateTime addressConfirmedAt;

    public static OrderShippingAddressResponseDto from(Order order) {
        OrderShippingAddress address = order.getShippingAddress();
        return new OrderShippingAddressResponseDto(
                order.getOrderId(),
                order.getStatus(),
                address.getRecipientName(),
                address.getRecipientPhone(),
                address.getZipCode(),
                address.getAddress1(),
                address.getAddress2(),
                order.getAddressConfirmedAt()
        );
    }
}
