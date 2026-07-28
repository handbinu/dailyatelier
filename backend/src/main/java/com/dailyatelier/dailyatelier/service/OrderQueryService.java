package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.OrderDetailResponseDto;
import com.dailyatelier.dailyatelier.dto.OrderPageResponseDto;
import com.dailyatelier.dailyatelier.dto.OrderSummaryResponseDto;
import com.dailyatelier.dailyatelier.entity.Order;
import com.dailyatelier.dailyatelier.entity.OrderStatus;
import com.dailyatelier.dailyatelier.exception.OrderApiException;
import com.dailyatelier.dailyatelier.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final Sort ORDER_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("orderId")
    );

    private final OrderRepository orderRepository;

    public OrderPageResponseDto getBuyerOrders(
            String userId,
            OrderStatus status,
            int page,
            int size) {
        PageRequest pageable = pageable(page, size);
        Page<Order> orders = status == null
                ? orderRepository.findByBuyer_UserId(userId, pageable)
                : orderRepository.findByBuyer_UserIdAndStatus(
                        userId,
                        status,
                        pageable
                );
        List<OrderSummaryResponseDto> content = orders.getContent()
                .stream()
                .map(OrderSummaryResponseDto::forBuyer)
                .toList();
        return pageResponse(
                orders,
                content,
                statusCounts(
                        orderRepository.countByBuyerUserIdGrouped(userId)
                )
        );
    }

    public OrderDetailResponseDto getBuyerOrder(
            String userId,
            Long orderId) {
        Order order = findOrder(orderId);
        if (!order.getBuyerIdSnapshot().equals(userId)) {
            throw accessDenied("본인의 주문만 조회할 수 있습니다.");
        }
        return OrderDetailResponseDto.forBuyer(order);
    }

    public OrderPageResponseDto getSellerOrders(
            String userId,
            OrderStatus status,
            int page,
            int size) {
        PageRequest pageable = pageable(page, size);
        Page<Order> orders = status == null
                ? orderRepository.findBySeller_UserId(userId, pageable)
                : orderRepository.findBySeller_UserIdAndStatus(
                        userId,
                        status,
                        pageable
                );
        List<OrderSummaryResponseDto> content = orders.getContent()
                .stream()
                .map(OrderSummaryResponseDto::forSeller)
                .toList();
        return pageResponse(
                orders,
                content,
                statusCounts(
                        orderRepository.countBySellerUserIdGrouped(userId)
                )
        );
    }

    public OrderDetailResponseDto getSellerOrder(
            String userId,
            Long orderId) {
        Order order = findOrder(orderId);
        if (!order.getSellerIdSnapshot().equals(userId)) {
            throw accessDenied("본인의 판매 주문만 조회할 수 있습니다.");
        }
        return OrderDetailResponseDto.forSeller(order);
    }

    private PageRequest pageable(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new OrderApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGE_REQUEST",
                    "페이지는 0 이상, 페이지 크기는 1 이상 50 이하여야 합니다."
            );
        }
        return PageRequest.of(page, size, ORDER_SORT);
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderApiException(
                        HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND",
                        "주문을 찾을 수 없습니다."
                ));
    }

    private OrderPageResponseDto pageResponse(
            Page<Order> orders,
            List<OrderSummaryResponseDto> content,
            Map<OrderStatus, Long> counts) {
        return new OrderPageResponseDto(
                content,
                orders.getNumber(),
                orders.getSize(),
                orders.getTotalElements(),
                orders.getTotalPages(),
                counts
        );
    }

    private Map<OrderStatus, Long> statusCounts(
            List<OrderRepository.OrderStatusCountProjection> rows) {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            counts.put(status, 0L);
        }
        for (OrderRepository.OrderStatusCountProjection row : rows) {
            counts.put(row.getStatus(), row.getTotal());
        }
        return counts;
    }

    private OrderApiException accessDenied(String message) {
        return new OrderApiException(
                HttpStatus.FORBIDDEN,
                "ORDER_ACCESS_DENIED",
                message
        );
    }
}
