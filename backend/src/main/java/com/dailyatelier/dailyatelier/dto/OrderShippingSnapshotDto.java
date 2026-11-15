package com.dailyatelier.dailyatelier.dto;

import com.dailyatelier.dailyatelier.entity.OrderShippingAddress;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderShippingSnapshotDto {
    private String recipientName;
    private String recipientPhone;
    private String zipCode;
    private String address1;
    private String address2;

    public static OrderShippingSnapshotDto from(
            OrderShippingAddress address) {
        if (address == null) {
            return null;
        }
        return new OrderShippingSnapshotDto(
                address.getRecipientName(),
                address.getRecipientPhone(),
                address.getZipCode(),
                address.getAddress1(),
                address.getAddress2()
        );
    }
}
