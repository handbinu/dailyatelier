package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.config.SecurityConfig;
import com.dailyatelier.dailyatelier.dto.ArtDeleteResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtDetailResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtResponseDto;
import com.dailyatelier.dailyatelier.jwt.JwtTokenProvider;
import com.dailyatelier.dailyatelier.exception.DomainApiException;
import com.dailyatelier.dailyatelier.service.ArtService;
import com.dailyatelier.dailyatelier.service.ArtSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    private ArtSearchService artSearchService;

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
    void ownerCanUpdateArt() throws Exception {
        ArtResponseDto art = createArtResponse(4L, 0);
        when(artService.updateArt(
                org.mockito.ArgumentMatchers.eq(4L),
                org.mockito.ArgumentMatchers.eq("owner"),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(art);

        mockMvc.perform(patch("/api/arts/4")
                        .with(authentication(stringAuthentication("owner")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "descript": "변경 설명"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artId").value(4));
    }

    @Test
    void ownerCanDeleteOrCancelArt() throws Exception {
        ArtDeleteResponseDto response = new ArtDeleteResponseDto(
                5L,
                ArtDeleteResponseDto.Action.CANCELED,
                3
        );
        when(artService.deleteArt(5L, "owner")).thenReturn(response);

        mockMvc.perform(delete("/api/arts/5")
                        .with(authentication(stringAuthentication("owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artId").value(5))
                .andExpect(jsonPath("$.action").value("CANCELED"))
                .andExpect(jsonPath("$.artStatus").value(3));
    }

    @Test
    void anonymousUserCannotUpdateOrDeleteArt() throws Exception {
        mockMvc.perform(patch("/api/arts/4")
                        .contentType("application/json")
                        .content("{\"descript\":\"변경\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/arts/4"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emptyUpdateRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/arts/4")
                        .with(authentication(stringAuthentication("owner")))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatePolicyErrorReturnsBadRequest() throws Exception {
        when(artService.updateArt(
                org.mockito.ArgumentMatchers.eq(4L),
                org.mockito.ArgumentMatchers.eq("owner"),
                org.mockito.ArgumentMatchers.any()
        )).thenThrow(new DomainApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_AUCTION_PERIOD",
                "Closing time must be after bid start time and current time"
        ));

        mockMvc.perform(patch("/api/arts/4")
                        .with(authentication(stringAuthentication("owner")))
                        .contentType("application/json")
                        .content("{\"closingTime\":\"2026-07-01T10:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_AUCTION_PERIOD"))
                .andExpect(jsonPath("$.message")
                        .value("Closing time must be after bid start time and current time"))
                .andExpect(jsonPath("$.path").value("/api/arts/4"));
    }

    @Test
    void missingDeleteTargetReturnsNotFound() throws Exception {
        when(artService.deleteArt(404L, "owner"))
                .thenThrow(new DomainApiException(
                        HttpStatus.NOT_FOUND,
                        "ART_NOT_FOUND",
                        "Art not found"
                ));

        mockMvc.perform(delete("/api/arts/404")
                        .with(authentication(stringAuthentication("owner"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("ART_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Art not found"))
                .andExpect(jsonPath("$.path").value("/api/arts/404"));
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

    private UsernamePasswordAuthenticationToken stringAuthentication(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}
