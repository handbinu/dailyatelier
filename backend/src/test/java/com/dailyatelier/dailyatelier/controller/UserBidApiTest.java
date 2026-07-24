package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.config.SecurityConfig;
import com.dailyatelier.dailyatelier.dto.BidStatusResponseDto;
import com.dailyatelier.dailyatelier.jwt.JwtTokenProvider;
import com.dailyatelier.dailyatelier.service.BidService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserBidController.class)
@Import(SecurityConfig.class)
class UserBidApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BidService bidService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void authenticatedUserCanGetOwnBidStatuses() throws Exception {
        BidStatusResponseDto bid = new BidStatusResponseDto(
                7L,
                "여름의 정원",
                "하루",
                "https://example.com/art.jpg",
                150_000,
                160_000,
                false,
                "IMMINENT",
                LocalDateTime.of(2026, 7, 23, 18, 30),
                LocalDateTime.of(2026, 7, 20, 10, 0),
                LocalDateTime.of(2026, 7, 24, 10, 0)
        );
        when(bidService.getMyBids("bidder", 0, 12))
                .thenReturn(new PageImpl<>(List.of(bid), PageRequest.of(0, 12), 1));

        mockMvc.perform(get("/api/users/me/bids")
                        .with(authentication(stringAuthentication("bidder"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].artId").value(7))
                .andExpect(jsonPath("$.content[0].myBidPrice").value(150000))
                .andExpect(jsonPath("$.content[0].currentPrice").value(160000))
                .andExpect(jsonPath("$.content[0].isLeading").value(false))
                .andExpect(jsonPath("$.content[0].auctionStatus").value("IMMINENT"));
    }

    @Test
    void anonymousUserCannotGetBidStatuses() throws Exception {
        mockMvc.perform(get("/api/users/me/bids"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private UsernamePasswordAuthenticationToken stringAuthentication(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}
