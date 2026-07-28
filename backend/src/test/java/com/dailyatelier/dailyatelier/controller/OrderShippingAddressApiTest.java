package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.config.SecurityConfig;
import com.dailyatelier.dailyatelier.dto.OrderShippingAddressResponseDto;
import com.dailyatelier.dailyatelier.entity.OrderStatus;
import com.dailyatelier.dailyatelier.exception.OrderApiException;
import com.dailyatelier.dailyatelier.jwt.JwtTokenProvider;
import com.dailyatelier.dailyatelier.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderShippingAddressApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void buyerCanConfirmShippingAddress() throws Exception {
        when(orderService.confirmShippingAddress(
                eq(7L),
                eq("buyer"),
                any()
        )).thenReturn(new OrderShippingAddressResponseDto(
                7L,
                OrderStatus.PAYMENT_PENDING,
                "구매자",
                "010-1234-5678",
                "02535",
                "서울특별시 중랑구",
                "101호",
                LocalDateTime.of(2026, 7, 28, 19, 0)
        ));

        mockMvc.perform(put("/api/users/me/orders/7/shipping-address")
                        .with(authentication(stringAuthentication("buyer")))
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(7))
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.zipCode").value("02535"))
                .andExpect(jsonPath("$.addressConfirmedAt")
                        .value("2026-07-28T19:00:00"));
    }

    @Test
    void anonymousUserCannotConfirmShippingAddress() throws Exception {
        mockMvc.perform(put("/api/users/me/orders/7/shipping-address")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void invalidAddressReturnsBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(put("/api/users/me/orders/7/shipping-address")
                        .with(authentication(stringAuthentication("buyer")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "recipientName": "구매자",
                                  "recipientPhone": "010-1234-5678",
                                  "zipCode": "1234",
                                  "address1": " ",
                                  "address2": "101호",
                                  "saveAsDefault": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_SHIPPING_ADDRESS"));

        verify(orderService, never()).confirmShippingAddress(
                any(),
                any(),
                any()
        );
    }

    @Test
    void paidOrderAddressChangeReturnsConflict() throws Exception {
        when(orderService.confirmShippingAddress(
                eq(7L),
                eq("buyer"),
                any()
        )).thenThrow(new OrderApiException(
                HttpStatus.CONFLICT,
                "SHIPPING_ADDRESS_LOCKED",
                "결제 대기 주문에서만 배송지를 변경할 수 있습니다."
        ));

        mockMvc.perform(put("/api/users/me/orders/7/shipping-address")
                        .with(authentication(stringAuthentication("buyer")))
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHIPPING_ADDRESS_LOCKED"));
    }

    private String validRequestJson() {
        return """
                {
                  "recipientName": "구매자",
                  "recipientPhone": "010-1234-5678",
                  "zipCode": "02535",
                  "address1": "서울특별시 중랑구",
                  "address2": "101호",
                  "saveAsDefault": true
                }
                """;
    }

    private UsernamePasswordAuthenticationToken stringAuthentication(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}
