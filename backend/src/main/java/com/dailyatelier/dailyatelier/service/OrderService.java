package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.OrderShippingAddressRequestDto;
import com.dailyatelier.dailyatelier.dto.OrderShippingAddressResponseDto;
import com.dailyatelier.dailyatelier.entity.Address;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Bid;
import com.dailyatelier.dailyatelier.entity.Order;
import com.dailyatelier.dailyatelier.entity.OrderShippingAddress;
import com.dailyatelier.dailyatelier.entity.OrderStatus;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.exception.OrderApiException;
import com.dailyatelier.dailyatelier.repository.AddressRepository;
import com.dailyatelier.dailyatelier.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderService {
    static final long PAYMENT_DEADLINE_HOURS = 24;

    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;
    private final ShippingAddressPolicy shippingAddressPolicy;
    private final Clock clock;

    @Transactional
    public Order createForSoldAuction(
            Art art,
            Bid winningBid,
            LocalDateTime createdAt) {
        Optional<Order> existingOrder =
                orderRepository.findByArtArtId(art.getArtId());
        if (existingOrder.isPresent()) {
            return existingOrder.get();
        }

        User buyer = winningBid.getUser();
        User seller = art.getArtist().getUser();
        OrderShippingAddress shippingAddress = addressRepository
                .findById(buyer.getUserId())
                .flatMap(address ->
                        shippingAddressPolicy.fromDefaultAddress(buyer, address))
                .orElse(null);

        Order order = Order.create(
                art,
                winningBid,
                buyer,
                seller,
                createdAt,
                createdAt.plusHours(PAYMENT_DEADLINE_HOURS),
                shippingAddress
        );
        return orderRepository.save(order);
    }

    @Transactional
    public OrderShippingAddressResponseDto confirmShippingAddress(
            Long orderId,
            String userId,
            OrderShippingAddressRequestDto request) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderApiException(
                        HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND",
                        "주문을 찾을 수 없습니다."
                ));

        if (!order.getBuyer().getUserId().equals(userId)) {
            throw new OrderApiException(
                    HttpStatus.FORBIDDEN,
                    "ORDER_ACCESS_DENIED",
                    "본인의 주문 배송지만 변경할 수 있습니다."
            );
        }
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new OrderApiException(
                    HttpStatus.CONFLICT,
                    "SHIPPING_ADDRESS_LOCKED",
                    "결제 대기 주문에서만 배송지를 변경할 수 있습니다."
            );
        }

        OrderShippingAddress shippingAddress =
                shippingAddressPolicy.fromRequest(request);
        order.confirmShippingAddress(
                shippingAddress,
                LocalDateTime.now(clock)
        );

        if (request.isSaveAsDefault()) {
            saveDefaultAddress(order.getBuyer(), shippingAddress);
        }
        orderRepository.save(order);
        return OrderShippingAddressResponseDto.from(order);
    }

    private void saveDefaultAddress(
            User buyer,
            OrderShippingAddress shippingAddress) {
        Address address = addressRepository.findById(buyer.getUserId())
                .orElseGet(() -> {
                    Address newAddress = new Address();
                    newAddress.setUser(buyer);
                    return newAddress;
                });
        address.setZipCode(shippingAddress.getZipCode());
        address.setUserAddress1(shippingAddress.getAddress1());
        address.setUserAddress2(shippingAddress.getAddress2());
        addressRepository.save(address);
    }
}
