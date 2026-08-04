package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.config.SecurityConfig;
import com.dailyatelier.dailyatelier.dto.PointSummaryResponseDto;
import com.dailyatelier.dailyatelier.entity.PaymentProvider;
import com.dailyatelier.dailyatelier.entity.PointCharge;
import com.dailyatelier.dailyatelier.entity.PointChargeStatus;
import com.dailyatelier.dailyatelier.jwt.JwtTokenProvider;
import com.dailyatelier.dailyatelier.service.PointChargeService;
import com.dailyatelier.dailyatelier.service.PointQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PointController.class)
@Import(SecurityConfig.class)
class PointApiTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private PointQueryService pointQueryService;
    @MockitoBean
    private PointChargeService pointChargeService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void authenticatedUserCanReadSeparatedBalances() throws Exception {
        when(pointQueryService.getSummary("buyer"))
                .thenReturn(new PointSummaryResponseDto(70_000, 30_000));

        mockMvc.perform(get("/api/users/me/points")
                        .with(authentication(authToken("buyer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availablePoint").value(70000))
                .andExpect(jsonPath("$.heldPoint").value(30000));
    }

    @Test
    void chargeRequiresIdempotencyKeyAndUsesAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/users/me/points/charges")
                        .with(authentication(authToken("buyer")))
                        .contentType("application/json")
                        .content("{\"amount\":10000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_POINT_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("충전 금액과 멱등성 키를 확인해 주세요."))
                .andExpect(jsonPath("$.path")
                        .value("/api/users/me/points/charges"));

        PointCharge charge = mock(PointCharge.class);
        when(charge.getChargeId()).thenReturn(7L);
        when(charge.getProvider()).thenReturn(PaymentProvider.INTERNAL);
        when(charge.getStatus()).thenReturn(PointChargeStatus.PAID);
        when(charge.getRequestedAmount()).thenReturn(10_000L);
        when(charge.getPaidAmount()).thenReturn(10_000L);
        when(pointChargeService.create(eq("buyer"), any(), eq(10_000L), eq("charge-key")))
                .thenReturn(charge);
        when(pointChargeService.approve(eq(7L), any())).thenReturn(charge);

        mockMvc.perform(post("/api/users/me/points/charges")
                        .with(authentication(authToken("buyer")))
                        .header("Idempotency-Key", "charge-key")
                        .contentType("application/json")
                        .content("{\"amount\":10000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.paidAmount").value(10000));

        verify(pointChargeService).create(eq("buyer"), any(), eq(10_000L), eq("charge-key"));
    }

    @Test
    void anonymousUserCannotReadPointData() throws Exception {
        mockMvc.perform(get("/api/users/me/points"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.path").value("/api/users/me/points"));
    }

    private UsernamePasswordAuthenticationToken authToken(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}
