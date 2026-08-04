package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.config.SecurityConfig;
import com.dailyatelier.dailyatelier.dto.BidCreateRequestDto;
import com.dailyatelier.dailyatelier.dto.BidCreateResponseDto;
import com.dailyatelier.dailyatelier.exception.BidApiException;
import com.dailyatelier.dailyatelier.jwt.JwtTokenProvider;
import com.dailyatelier.dailyatelier.service.BidService;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BidController.class)
@Import(SecurityConfig.class)
class BidApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BidService bidService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void authenticatedUserCanCreateBid() throws Exception {
        when(bidService.createBid(eq(7L), eq("bidder"), any(BidCreateRequestDto.class)))
                .thenReturn(new BidCreateResponseDto(
                        31L,
                        7L,
                        150_000,
                        150_000,
                        LocalDateTime.of(2026, 7, 23, 18, 30),
                        50_000,
                        150_000
                ));

        mockMvc.perform(post("/api/arts/7/bids")
                        .with(authentication(stringAuthentication("bidder")))
                        .contentType("application/json")
                        .content("""
                                {"bidPrice": 150000}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bidId").value(31))
                .andExpect(jsonPath("$.artId").value(7))
                .andExpect(jsonPath("$.bidPrice").value(150000))
                .andExpect(jsonPath("$.currentPrice").value(150000))
                .andExpect(jsonPath("$.availablePoint").value(50000))
                .andExpect(jsonPath("$.heldPoint").value(150000));
    }

    @Test
    void anonymousUserGetsStructuredUnauthorizedResponse() throws Exception {
        mockMvc.perform(post("/api/arts/7/bids")
                        .contentType("application/json")
                        .content("""
                                {"bidPrice": 150000}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.path").value("/api/arts/7/bids"));
    }

    @Test
    void invalidAmountGetsStructuredBadRequestResponse() throws Exception {
        mockMvc.perform(post("/api/arts/7/bids")
                        .with(authentication(stringAuthentication("bidder")))
                        .contentType("application/json")
                        .content("""
                                {"bidPrice": 2100000001}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BID_AMOUNT"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/arts/7/bids"));
    }

    @Test
    void domainErrorUsesContractedStatusAndCode() throws Exception {
        when(bidService.createBid(eq(7L), eq("seller"), any(BidCreateRequestDto.class)))
                .thenThrow(new BidApiException(
                        HttpStatus.FORBIDDEN,
                        "SELF_BID_NOT_ALLOWED",
                        "본인 작품에는 입찰할 수 없습니다."
                ));

        mockMvc.perform(post("/api/arts/7/bids")
                        .with(authentication(stringAuthentication("seller")))
                        .contentType("application/json")
                        .content("""
                                {"bidPrice": 150000}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("SELF_BID_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value("본인 작품에는 입찰할 수 없습니다."))
                .andExpect(jsonPath("$.path").value("/api/arts/7/bids"));
    }

    private UsernamePasswordAuthenticationToken stringAuthentication(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}
