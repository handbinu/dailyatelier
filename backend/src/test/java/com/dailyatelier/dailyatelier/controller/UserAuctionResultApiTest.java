package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.config.SecurityConfig;
import com.dailyatelier.dailyatelier.dto.WinningArtResponseDto;
import com.dailyatelier.dailyatelier.jwt.JwtTokenProvider;
import com.dailyatelier.dailyatelier.service.AuctionResultService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
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

@WebMvcTest(UserAuctionResultController.class)
@Import(SecurityConfig.class)
class UserAuctionResultApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuctionResultService auctionResultService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void authenticatedUserCanGetOnlyPublicWinningArtFields() throws Exception {
        WinningArtResponseDto result = new WinningArtResponseDto(
                7L,
                "여름의 정원",
                "하루",
                "https://example.com/art.jpg",
                160_000,
                LocalDateTime.of(2026, 7, 27, 18, 0, 4)
        );
        when(auctionResultService.getMyWins("winner", 0, 12))
                .thenReturn(new PageImpl<>(List.of(result), PageRequest.of(0, 12), 1));

        mockMvc.perform(get("/api/users/me/wins")
                        .with(authentication(stringAuthentication("winner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].artId").value(7))
                .andExpect(jsonPath("$.content[0].winningPrice").value(160000))
                .andExpect(jsonPath("$.content[0].closedAt")
                        .value("2026-07-27T18:00:04"))
                .andExpect(jsonPath("$.content[0].winningBidderNickname").doesNotExist())
                .andExpect(jsonPath("$.content[0].winningUserId").doesNotExist());
    }

    @Test
    void authenticatedUserCanGetEmptyWinningArtPage() throws Exception {
        when(auctionResultService.getMyWins("winner", 0, 12))
                .thenReturn(Page.empty(PageRequest.of(0, 12)));

        mockMvc.perform(get("/api/users/me/wins")
                        .with(authentication(stringAuthentication("winner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void anonymousUserCannotGetWinningArts() throws Exception {
        mockMvc.perform(get("/api/users/me/wins"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private UsernamePasswordAuthenticationToken stringAuthentication(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}
