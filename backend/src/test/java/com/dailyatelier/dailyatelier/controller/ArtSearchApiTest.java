package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.config.SecurityConfig;
import com.dailyatelier.dailyatelier.dto.ArtSearchCriteria;
import com.dailyatelier.dailyatelier.dto.ArtSearchResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtSearchSort;
import com.dailyatelier.dailyatelier.dto.ArtSearchStatus;
import com.dailyatelier.dailyatelier.entity.ArtCategory;
import com.dailyatelier.dailyatelier.entity.ArtFormat;
import com.dailyatelier.dailyatelier.jwt.JwtTokenProvider;
import com.dailyatelier.dailyatelier.service.ArtSearchService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ArtController.class)
@Import(SecurityConfig.class)
class ArtSearchApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArtService artService;

    @MockitoBean
    private ArtSearchService artSearchService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void anonymousRequestUsesDefaultsAndReturnsPageAndCardContract() throws Exception {
        ArtSearchResponseDto art = artResponse();
        when(artSearchService.search(
                new ArtSearchCriteria(null, null, null, null, null, ArtSearchSort.ENDING_SOON),
                0, 12
        )).thenReturn(new PageImpl<>(List.of(art), PageRequest.of(0, 12), 25));

        mockMvc.perform(get("/api/arts/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].artId").value(7))
                .andExpect(jsonPath("$.content[0].artistCode").value("artist-code"))
                .andExpect(jsonPath("$.content[0].artistName").value("테스트 작가"))
                .andExpect(jsonPath("$.content[0].name").value("테스트 작품"))
                .andExpect(jsonPath("$.content[0].format").value("PHYSICAL"))
                .andExpect(jsonPath("$.content[0].category").value("OIL_PAINTING"))
                .andExpect(jsonPath("$.content[0].currentPrice").value(120000))
                .andExpect(jsonPath("$.content[0].bidStartTime").exists())
                .andExpect(jsonPath("$.content[0].closingTime").exists())
                .andExpect(jsonPath("$.content[0].imgPath").value("art.jpg"))
                .andExpect(jsonPath("$.content[0].status").value("ONGOING"))
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(12))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void bindsEverySearchFilterSortAndPageParameter() throws Exception {
        when(artSearchService.search(
                new ArtSearchCriteria("해변", "김", ArtFormat.DIGITAL,
                        ArtCategory.DIGITAL_ART, ArtSearchStatus.UPCOMING,
                        ArtSearchSort.PRICE_DESC),
                2, 25
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 25), 0));

        mockMvc.perform(get("/api/arts/search")
                        .param("q", "해변")
                        .param("artist", "김")
                        .param("format", "DIGITAL")
                        .param("category", "DIGITAL_ART")
                        .param("status", "UPCOMING")
                        .param("sort", "PRICE_DESC")
                        .param("page", "2")
                        .param("size", "25"))
                .andExpect(status().isOk());
    }

    @Test
    void blankTextFiltersArePassedToServiceForNormalization() throws Exception {
        when(artSearchService.search(
                new ArtSearchCriteria("   ", " ", null, null, null, ArtSearchSort.NEWEST),
                0, 12
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 12), 0));

        mockMvc.perform(get("/api/arts/search")
                        .param("q", "   ")
                        .param("artist", " ")
                        .param("sort", "NEWEST"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidEnumAndPaginationReturnCommonInvalidRequestResponse() throws Exception {
        assertInvalid("format", "VIDEO");
        assertInvalid("category", "PAINTING");
        assertInvalid("status", "LIVE");
        assertInvalid("sort", "POPULAR");
        assertInvalid("page", "-1");
        assertInvalid("page", "text");
        assertInvalid("size", "0");
        assertInvalid("size", "51");
    }

    @Test
    void expiredTokenDoesNotBlockPublicSearch() throws Exception {
        when(jwtTokenProvider.validateToken(anyString())).thenReturn(false);
        when(artSearchService.search(
                new ArtSearchCriteria(null, null, null, null, null, ArtSearchSort.ENDING_SOON),
                0, 12
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 12), 0));

        mockMvc.perform(get("/api/arts/search")
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isOk());
    }

    private void assertInvalid(String parameter, String value) throws Exception {
        mockMvc.perform(get("/api/arts/search").param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("요청값을 확인해 주세요."))
                .andExpect(jsonPath("$.path").value("/api/arts/search"));
    }

    private ArtSearchResponseDto artResponse() {
        return new ArtSearchResponseDto(
                7L, "artist-code", "테스트 작가", "테스트 작품",
                ArtFormat.PHYSICAL, ArtCategory.OIL_PAINTING, 120_000,
                LocalDateTime.of(2026, 8, 11, 10, 0),
                LocalDateTime.of(2026, 8, 12, 10, 0),
                "art.jpg", ArtSearchStatus.ONGOING,
                LocalDateTime.of(2026, 8, 1, 10, 0)
        );
    }
}
