package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.OrderShippingAddressRequestDto;
import com.dailyatelier.dailyatelier.entity.Address;
import com.dailyatelier.dailyatelier.entity.OrderShippingAddress;
import com.dailyatelier.dailyatelier.entity.User;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ShippingAddressPolicy {

    public Optional<OrderShippingAddress> fromDefaultAddress(
            User user,
            Address address) {
        if (user == null || address == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(OrderShippingAddress.of(
                    user.getName(),
                    user.getPhoneNumber(),
                    address.getZipCode(),
                    address.getUserAddress1(),
                    address.getUserAddress2()
            ));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public OrderShippingAddress fromRequest(
            OrderShippingAddressRequestDto request) {
        return OrderShippingAddress.of(
                request.getRecipientName(),
                request.getRecipientPhone(),
                request.getZipCode(),
                request.getAddress1(),
                request.getAddress2()
        );
    }
}
