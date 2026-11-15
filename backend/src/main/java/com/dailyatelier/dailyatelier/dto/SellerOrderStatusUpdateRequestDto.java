package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SellerOrderStatusUpdateRequestDto {
    @NotNull
    private OrderStatus status;

    @Size(max = 50)
    private String shippingCarrier;

    @Size(max = 100)
    private String trackingNumber;
}
