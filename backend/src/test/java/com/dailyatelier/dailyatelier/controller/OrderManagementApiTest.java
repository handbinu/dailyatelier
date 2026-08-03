package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.config.SecurityConfig;
import com.dailyatelier.dailyatelier.dto.OrderPageResponseDto;
import com.dailyatelier.dailyatelier.entity.Order;
import com.dailyatelier.dailyatelier.entity.OrderStatus;
import com.dailyatelier.dailyatelier.exception.OrderApiException;
import com.dailyatelier.dailyatelier.jwt.JwtTokenProvider;
import com.dailyatelier.dailyatelier.service.OrderQueryService;
import com.dailyatelier.dailyatelier.service.OrderService;
import com.dailyatelier.dailyatelier.service.OrderStateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.EnumMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        OrderController.class,
        SellerOrderController.class
})
@Import(SecurityConfig.class)
class OrderManagementApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OrderQueryService orderQueryService;

    @MockitoBean
    private OrderStateService orderStateService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void buyerCanFilterOwnOrdersUsingAuthenticatedPrincipal() throws Exception {
        when(orderQueryService.getBuyerOrders(
                "buyer",
                OrderStatus.PAID,
                0,
                12
        )).thenReturn(emptyPage());

        mockMvc.perform(get("/api/users/me/orders")
                        .param("status", "PAID")
                        .with(authentication(userAuthentication("buyer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.statusCounts.PAYMENT_PENDING").value(0))
                .andExpect(jsonPath("$.statusCounts.PAID").value(0));

        verify(orderQueryService).getBuyerOrders(
                "buyer",
                OrderStatus.PAID,
                0,
                12
        );
    }

    @Test
    void buyerActionsIgnoreBodyUserIdAndUsePrincipal() throws Exception {
        Order canceledOrder = mockOrder(7L, OrderStatus.CANCELED);
        when(orderStateService.cancelPending(7L, "buyer"))
                .thenReturn(canceledOrder);

        mockMvc.perform(post("/api/users/me/orders/7/cancel")
                        .with(authentication(userAuthentication("buyer")))
                        .contentType("application/json")
                        .content("""
                                {"userId":"attacker"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(7))
                .andExpect(jsonPath("$.status").value("CANCELED"));

        verify(orderStateService).cancelPending(7L, "buyer");
    }

    @Test
    void paymentRequiresIdempotencyKeyAndUsesAuthenticatedBuyer() throws Exception {
        Order paidOrder = mockOrder(7L, OrderStatus.PAID);
        when(orderStateService.markPaid(7L, "buyer", "payment-key"))
                .thenReturn(paidOrder);

        mockMvc.perform(post("/api/users/me/orders/7/payment")
                        .with(authentication(userAuthentication("buyer")))
                        .header("Idempotency-Key", "payment-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        verify(orderStateService).markPaid(7L, "buyer", "payment-key");

        mockMvc.perform(post("/api/users/me/orders/7/payment")
                        .with(authentication(userAuthentication("buyer"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousUserCannotReadBuyerOrders() throws Exception {
        mockMvc.perform(get("/api/users/me/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void buyerDetailErrorsUseStructuredNotFoundAndForbiddenCodes() throws Exception {
        when(orderQueryService.getBuyerOrder("buyer", 404L))
                .thenThrow(new OrderApiException(
                        HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND",
                        "주문을 찾을 수 없습니다."
                ));
        when(orderQueryService.getBuyerOrder("buyer", 8L))
                .thenThrow(new OrderApiException(
                        HttpStatus.FORBIDDEN,
                        "ORDER_ACCESS_DENIED",
                        "본인의 주문만 조회할 수 있습니다."
                ));

        mockMvc.perform(get("/api/users/me/orders/404")
                        .with(authentication(userAuthentication("buyer"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        mockMvc.perform(get("/api/users/me/orders/8")
                        .with(authentication(userAuthentication("buyer"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORDER_ACCESS_DENIED"));
    }

    @Test
    void buyerConfirmReturnsLatestOrderAndStatusConflictIsStructured() throws Exception {
        Order confirmedOrder = mockOrder(7L, OrderStatus.CONFIRMED);
        when(orderStateService.confirm(7L, "buyer"))
                .thenReturn(confirmedOrder);

        mockMvc.perform(post("/api/users/me/orders/7/confirm")
                        .with(authentication(userAuthentication("buyer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        when(orderStateService.confirm(8L, "buyer"))
                .thenThrow(new OrderApiException(
                        HttpStatus.CONFLICT,
                        "ORDER_STATUS_CONFLICT",
                        "배송 완료 주문만 구매 확정할 수 있습니다."
                ));

        mockMvc.perform(post("/api/users/me/orders/8/confirm")
                        .with(authentication(userAuthentication("buyer"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_STATUS_CONFLICT"));
    }

    @Test
    void buyerCanMarkShippedOrderDeliveredAndRequestRefund() throws Exception {
        Order deliveredOrder = mockOrder(7L, OrderStatus.DELIVERED);
        Order paidOrder = mockOrder(8L, OrderStatus.PAID);
        when(orderStateService.markDelivered(7L, "buyer"))
                .thenReturn(deliveredOrder);
        when(orderStateService.requestRefund(8L, "buyer", "작품 상태 문제"))
                .thenReturn(paidOrder);

        mockMvc.perform(post("/api/users/me/orders/7/delivered")
                        .with(authentication(userAuthentication("buyer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        mockMvc.perform(post("/api/users/me/orders/8/refund-request")
                        .with(authentication(userAuthentication("buyer")))
                        .contentType("application/json")
                        .content("""
                                {"reason":"작품 상태 문제"}
                                """))
                .andExpect(status().isOk());

        verify(orderStateService).markDelivered(7L, "buyer");
        verify(orderStateService).requestRefund(8L, "buyer", "작품 상태 문제");
    }

    @Test
    void refundRequestRequiresReason() throws Exception {
        mockMvc.perform(post("/api/users/me/orders/8/refund-request")
                        .with(authentication(userAuthentication("buyer")))
                        .contentType("application/json")
                        .content("""
                                {"reason":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_REQUEST"));
    }

    @Test
    void artistCanReadAndPrepareOwnSalesUsingPrincipal() throws Exception {
        when(orderQueryService.getSellerOrders(
                "seller",
                null,
                0,
                12
        )).thenReturn(emptyPage());
        Order preparingOrder = mockOrder(7L, OrderStatus.PREPARING);
        when(orderStateService.startPreparing(7L, "seller"))
                .thenReturn(preparingOrder);

        mockMvc.perform(get("/api/artists/me/orders")
                        .with(authentication(artistAuthentication("seller"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/artists/me/orders/7/status")
                        .with(authentication(artistAuthentication("seller")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "status":"PREPARING",
                                  "sellerId":"attacker"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"));

        verify(orderQueryService).getSellerOrders(
                "seller",
                null,
                0,
                12
        );
        verify(orderStateService).startPreparing(7L, "seller");
    }

    @Test
    void normalUserCannotAccessArtistOrderApi() throws Exception {
        mockMvc.perform(get("/api/artists/me/orders")
                        .with(authentication(userAuthentication("buyer"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void sellerCanShipWithCarrierAndTrackingNumber() throws Exception {
        Order shippedOrder = mockOrder(7L, OrderStatus.SHIPPED);
        when(orderStateService.ship(
                7L,
                "seller",
                "우체국택배",
                "1234-5678"
        )).thenReturn(shippedOrder);

        mockMvc.perform(patch("/api/artists/me/orders/7/status")
                        .with(authentication(artistAuthentication("seller")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "status":"SHIPPED",
                                  "shippingCarrier":"우체국택배",
                                  "trackingNumber":"1234-5678"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));

        verify(orderStateService).ship(
                7L,
                "seller",
                "우체국택배",
                "1234-5678"
        );
    }

    @Test
    void sellerCanApproveOrRejectPendingRefundUsingPrincipal() throws Exception {
        Order refundedOrder = mockOrder(7L, OrderStatus.REFUNDED);
        Order paidOrder = mockOrder(8L, OrderStatus.PAID);
        when(orderStateService.approveRefund(7L, "seller", "refund-key"))
                .thenReturn(refundedOrder);
        when(orderStateService.rejectRefund(8L, "seller"))
                .thenReturn(paidOrder);

        mockMvc.perform(post("/api/artists/me/orders/7/refund/approve")
                        .with(authentication(artistAuthentication("seller")))
                        .header("Idempotency-Key", "refund-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        mockMvc.perform(post("/api/artists/me/orders/8/refund/reject")
                        .with(authentication(artistAuthentication("seller"))))
                .andExpect(status().isOk());

        verify(orderStateService).approveRefund(7L, "seller", "refund-key");
        verify(orderStateService).rejectRefund(8L, "seller");
    }

    @Test
    void otherArtistCannotShipSellerOrder() throws Exception {
        when(orderStateService.ship(
                7L,
                "other-seller",
                "우체국택배",
                "1234-5678"
        )).thenThrow(new OrderApiException(
                HttpStatus.FORBIDDEN,
                "ORDER_ACCESS_DENIED",
                "본인의 판매 주문만 처리할 수 있습니다."
        ));

        mockMvc.perform(patch("/api/artists/me/orders/7/status")
                        .with(authentication(
                                artistAuthentication("other-seller")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "status":"SHIPPED",
                                  "shippingCarrier":"우체국택배",
                                  "trackingNumber":"1234-5678"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("ORDER_ACCESS_DENIED"));

        verify(orderStateService).ship(
                7L,
                "other-seller",
                "우체국택배",
                "1234-5678"
        );
    }

    @Test
    void invalidSellerStatusAndShipmentReturnStructuredErrors() throws Exception {
        mockMvc.perform(patch("/api/artists/me/orders/7/status")
                        .with(authentication(artistAuthentication("seller")))
                        .contentType("application/json")
                        .content("""
                                {"status":"PAID"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_SELLER_ORDER_STATUS"));

        when(orderStateService.ship(
                eq(7L),
                eq("seller"),
                eq(""),
                eq("")
        )).thenThrow(new OrderApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_SHIPPING_INFO",
                "택배사와 송장번호는 필수입니다."
        ));

        mockMvc.perform(patch("/api/artists/me/orders/7/status")
                        .with(authentication(artistAuthentication("seller")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "status":"SHIPPED",
                                  "shippingCarrier":"",
                                  "trackingNumber":""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SHIPPING_INFO"));
    }

    @Test
    void invalidStatusFilterReturnsStructuredBadRequest() throws Exception {
        mockMvc.perform(get("/api/users/me/orders")
                        .param("status", "UNKNOWN")
                        .with(authentication(userAuthentication("buyer"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_REQUEST"));
    }

    private OrderPageResponseDto emptyPage() {
        EnumMap<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            counts.put(status, 0L);
        }
        return new OrderPageResponseDto(
                List.of(),
                0,
                12,
                0,
                0,
                counts
        );
    }

    private Order mockOrder(Long orderId, OrderStatus status) {
        Order order = mock(Order.class);
        when(order.getOrderId()).thenReturn(orderId);
        when(order.getStatus()).thenReturn(status);
        when(order.isShippingAddressConfirmed()).thenReturn(true);
        return order;
    }

    private UsernamePasswordAuthenticationToken userAuthentication(
            String userId) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    private UsernamePasswordAuthenticationToken artistAuthentication(
            String userId) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ARTIST"))
        );
    }
}
