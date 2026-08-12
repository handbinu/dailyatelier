package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.config.SecurityConfig;
import com.dailyatelier.dailyatelier.dto.ArtResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtistDetailResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtistSummaryResponseDto;
import com.dailyatelier.dailyatelier.exception.DomainApiException;
import com.dailyatelier.dailyatelier.jwt.JwtTokenProvider;
import com.dailyatelier.dailyatelier.service.ArtistQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ArtistController.class)
@Import(SecurityConfig.class)
class ArtistApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArtistQueryService artistQueryService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void anonymousUserCanSearchArtistsWithPaginationContract() throws Exception {
        ArtistSummaryResponseDto artist = new ArtistSummaryResponseDto(
                "artist-code", "테스트 작가", "전체 소개", 2L);
        when(artistQueryService.getArtists("작가", 1, 5))
                .thenReturn(new PageImpl<>(List.of(artist), PageRequest.of(1, 5), 6));

        mockMvc.perform(get("/api/artists")
                        .param("keyword", "작가")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].artistId").value("artist-code"))
                .andExpect(jsonPath("$.content[0].profileImagePath").value("/img/artist.png"))
                .andExpect(jsonPath("$.content[0].artistName").value("테스트 작가"))
                .andExpect(jsonPath("$.content[0].artistIntro").value("전체 소개"))
                .andExpect(jsonPath("$.content[0].activeArtCount").value(2))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6));
    }

    @Test
    void artistListUsesDefaultRequestValues() throws Exception {
        when(artistQueryService.getArtists(null, 0, 12))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 12), 0));

        mockMvc.perform(get("/api/artists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void anonymousUserCanGetArtistDetail() throws Exception {
        when(artistQueryService.getArtist("artist-code"))
                .thenReturn(new ArtistDetailResponseDto(
                        "artist-code", "테스트 작가", "상세 소개", 3L));

        mockMvc.perform(get("/api/artists/artist-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artistId").value("artist-code"))
                .andExpect(jsonPath("$.profileImagePath").value("/img/artist.png"))
                .andExpect(jsonPath("$.artistName").value("테스트 작가"))
                .andExpect(jsonPath("$.artistIntro").value("상세 소개"))
                .andExpect(jsonPath("$.activeArtCount").value(3));
    }

    @Test
    void anonymousUserCanGetArtistArts() throws Exception {
        ArtResponseDto art = createArtResponse();
        when(artistQueryService.getArtistArts("artist-code", 0, 12))
                .thenReturn(new PageImpl<>(List.of(art), PageRequest.of(0, 12), 1));

        mockMvc.perform(get("/api/artists/artist-code/arts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].artId").value(1))
                .andExpect(jsonPath("$.content[0].artistCode").value("artist-code"))
                .andExpect(jsonPath("$.content[0].name").value("테스트 작품"));
    }

    @Test
    void missingArtistUsesCommonNotFoundResponse() throws Exception {
        when(artistQueryService.getArtist("missing"))
                .thenThrow(new DomainApiException(
                        HttpStatus.NOT_FOUND,
                        "ARTIST_NOT_FOUND",
                        "Artist not found"));

        mockMvc.perform(get("/api/artists/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("ARTIST_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Artist not found"))
                .andExpect(jsonPath("$.path").value("/api/artists/missing"));
    }

    @Test
    void missingArtistArtsUseCommonNotFoundResponse() throws Exception {
        when(artistQueryService.getArtistArts("missing", 0, 12))
                .thenThrow(new DomainApiException(
                        HttpStatus.NOT_FOUND,
                        "ARTIST_NOT_FOUND",
                        "Artist not found"));

        mockMvc.perform(get("/api/artists/missing/arts"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTIST_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/artists/missing/arts"));
    }

    @Test
    void malformedPaginationUsesCommonBadRequestResponse() throws Exception {
        mockMvc.perform(get("/api/artists").param("page", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("요청값을 확인해 주세요."))
                .andExpect(jsonPath("$.path").value("/api/artists"));
    }

    @Test
    void expiredTokenDoesNotBlockPublicArtistRequests() throws Exception {
        when(jwtTokenProvider.validateToken(anyString())).thenReturn(false);
        when(artistQueryService.getArtists(null, 0, 12))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 12), 0));

        mockMvc.perform(get("/api/artists")
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isOk());
    }

    @Test
    void artistManagementApisKeepExistingAuthorizationRules() throws Exception {
        mockMvc.perform(get("/api/artists/me/orders"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/artists/me/orders")
                        .with(authentication(userAuthentication())))
                .andExpect(status().isForbidden());
    }

    private ArtResponseDto createArtResponse() {
        return new ArtResponseDto(
                1L,
                "artist-code",
                "테스트 작가",
                "테스트 작품",
                "작품 설명",
                "캔버스",
                "작품 소개",
                100_000,
                120_000,
                1_000,
                LocalDateTime.of(2026, 8, 1, 10, 0),
                LocalDateTime.of(2026, 8, 31, 18, 0),
                "https://example.com/art.jpg",
                0
        );
    }

    private UsernamePasswordAuthenticationToken userAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                "user",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
