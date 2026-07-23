package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.config.SecurityConfig;
import com.dailyatelier.dailyatelier.dto.ArtDetailResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtResponseDto;
import com.dailyatelier.dailyatelier.jwt.JwtTokenProvider;
import com.dailyatelier.dailyatelier.service.ArtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ArtController.class)
@Import(SecurityConfig.class)
class ArtApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArtService artService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void anonymousUserCanGetArtList() throws Exception {
        ArtResponseDto art = createArtResponse(1L, 0);
        when(artService.getActiveArts(0, 12))
                .thenReturn(new PageImpl<>(List.of(art), PageRequest.of(0, 12), 1));

        mockMvc.perform(get("/api/arts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].artId").value(1));
    }

    @Test
    void anonymousUserCanGetArtDetail() throws Exception {
        ArtDetailResponseDto art = createArtDetailResponse(2L);
        when(artService.getArt(2L, null)).thenReturn(art);

        mockMvc.perform(get("/api/arts/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artId").value(2))
                .andExpect(jsonPath("$.isOwner").value(false));
    }

    @Test
    void anonymousUserGetsUnauthorizedWhenCreatingArt() throws Exception {
        mockMvc.perform(post("/api/arts")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousUserGetsUnauthorizedForMyArts() throws Exception {
        mockMvc.perform(get("/api/users/me/arts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenDoesNotBlockPublicArtList() throws Exception {
        ArtResponseDto art = createArtResponse(3L, 0);
        when(jwtTokenProvider.validateToken(anyString())).thenReturn(false);
        when(artService.getActiveArts(0, 12))
                .thenReturn(new PageImpl<>(List.of(art), PageRequest.of(0, 12), 1));

        mockMvc.perform(get("/api/arts")
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].artId").value(3));
    }

    @Test
    void expiredTokenGetsUnauthorizedForMyArts() throws Exception {
        when(jwtTokenProvider.validateToken(anyString())).thenReturn(false);

        mockMvc.perform(get("/api/users/me/arts")
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized());
    }

    private ArtResponseDto createArtResponse(Long artId, int artStatus) {
        return new ArtResponseDto(
                artId,
                "artist-code",
                "테스트 작가",
                "테스트 작품",
                "작품 설명",
                "캔버스",
                "작가 소개",
                100_000,
                120_000,
                LocalDateTime.of(2026, 7, 1, 10, 0),
                LocalDateTime.of(2026, 7, 31, 18, 0),
                "https://example.com/art.jpg",
                artStatus
        );
    }

    private ArtDetailResponseDto createArtDetailResponse(Long artId) {
        ArtResponseDto art = createArtResponse(artId, 0);
        return new ArtDetailResponseDto(
                art.getArtId(),
                art.getArtistCode(),
                art.getArtistName(),
                art.getName(),
                art.getDescript(),
                art.getMaterial(),
                art.getWIntro(),
                art.getStartPrice(),
                art.getCurrentPrice(),
                art.getBidStartTime(),
                art.getClosingTime(),
                art.getImgPath(),
                art.getArtStatus(),
                false
        );
    }
}
