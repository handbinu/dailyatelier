package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.config.SecurityConfig;
import com.dailyatelier.dailyatelier.dto.MyArtResponseDto;
import com.dailyatelier.dailyatelier.dto.MyArtState;
import com.dailyatelier.dailyatelier.jwt.JwtTokenProvider;
import com.dailyatelier.dailyatelier.service.ArtService;
import com.dailyatelier.dailyatelier.service.UserService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserArtApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArtService artService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void artistCanFilterEndedArtsWithoutWinnerIdentity() throws Exception {
        MyArtResponseDto result = new MyArtResponseDto(
                7L,
                "artist-code",
                "하루",
                "여름의 정원",
                "작품 설명",
                "캔버스",
                "작가 소개",
                100_000,
                160_000,
                LocalDateTime.of(2026, 7, 20, 10, 0),
                LocalDateTime.of(2026, 7, 27, 18, 0),
                "https://example.com/art.jpg",
                2,
                LocalDateTime.of(2026, 7, 27, 18, 0, 4),
                160_000,
                3L,
                "SOLD"
        );
        when(artService.getMyArts("artist", MyArtState.ENDED, 0, 12))
                .thenReturn(new PageImpl<>(List.of(result), PageRequest.of(0, 12), 1));

        mockMvc.perform(get("/api/users/me/arts")
                        .param("state", "ENDED")
                        .with(authentication(stringAuthentication("artist"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].result").value("SOLD"))
                .andExpect(jsonPath("$.content[0].winningPrice").value(160000))
                .andExpect(jsonPath("$.content[0].bidCount").value(3))
                .andExpect(jsonPath("$.content[0].winningBidderNickname").doesNotExist())
                .andExpect(jsonPath("$.content[0].winningUserId").doesNotExist());

        verify(artService).getMyArts("artist", MyArtState.ENDED, 0, 12);
    }

    @Test
    void myArtsDefaultsToAllState() throws Exception {
        when(artService.getMyArts("artist", MyArtState.ALL, 0, 12))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 12), 0));

        mockMvc.perform(get("/api/users/me/arts")
                        .with(authentication(stringAuthentication("artist"))))
                .andExpect(status().isOk());

        verify(artService).getMyArts("artist", MyArtState.ALL, 0, 12);
    }

    @Test
    void invalidMyArtStateReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/users/me/arts")
                        .param("state", "UNKNOWN")
                        .with(authentication(stringAuthentication("artist"))))
                .andExpect(status().isBadRequest());
    }

    private UsernamePasswordAuthenticationToken stringAuthentication(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}
