package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.config.SecurityConfig;
import com.dailyatelier.dailyatelier.dto.ArtistReviewPageResponseDto;
import com.dailyatelier.dailyatelier.dto.ReviewPageResponseDto;
import com.dailyatelier.dailyatelier.dto.ReviewResponseDto;
import com.dailyatelier.dailyatelier.dto.ReviewSort;
import com.dailyatelier.dailyatelier.exception.ReviewApiException;
import com.dailyatelier.dailyatelier.jwt.JwtTokenProvider;
import com.dailyatelier.dailyatelier.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ReviewController.class, ArtistReviewController.class})
@Import(SecurityConfig.class)
class ReviewApiTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void createUsesPrincipalAndOnlyOrderStarContentContract() throws Exception {
        when(reviewService.create(
                org.mockito.ArgumentMatchers.eq("buyer"),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(review(31L, 7L));

        mockMvc.perform(post("/api/users/me/reviews")
                        .with(authentication(user("buyer")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "orderId":7,
                                  "star":9,
                                  "content":"조작값과 무관하게 저장되는 충분히 긴 리뷰",
                                  "userId":"attacker",
                                  "artId":999
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewId").value(31))
                .andExpect(jsonPath("$.orderId").value(7));

        verify(reviewService).create(
                org.mockito.ArgumentMatchers.eq("buyer"),
                argThat(request -> request.getOrderId().equals(7L)
                        && request.getStar().equals(9)
                        && request.getContent().contains("조작값과 무관"))
        );
    }

    @Test
    void invalidReviewRequestReturnsCommonBadRequestShape() throws Exception {
        mockMvc.perform(post("/api/users/me/reviews")
                        .with(authentication(user("buyer")))
                        .contentType("application/json")
                        .content("""
                                {"orderId":7,"star":11,"content":"짧음"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("요청값을 확인해 주세요."))
                .andExpect(jsonPath("$.path").value("/api/users/me/reviews"));
    }

    @Test
    void domainErrorsKeepReviewStatusAndCommonErrorShape() throws Exception {
        when(reviewService.update(
                org.mockito.ArgumentMatchers.eq("buyer"),
                org.mockito.ArgumentMatchers.eq(99L),
                org.mockito.ArgumentMatchers.any()
        )).thenThrow(new ReviewApiException(
                HttpStatus.FORBIDDEN,
                "REVIEW_ACCESS_DENIED",
                "본인이 작성한 리뷰만 수정할 수 있습니다."
        ));

        mockMvc.perform(put("/api/users/me/reviews/99")
                        .with(authentication(user("buyer")))
                        .contentType("application/json")
                        .content("""
                                {"star":8,"content":"수정 요청에 사용할 충분히 긴 리뷰"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("REVIEW_ACCESS_DENIED"))
                .andExpect(jsonPath("$.path").value("/api/users/me/reviews/99"));
    }

    @Test
    void listEndpointsPassServerSortFilterAndPagination() throws Exception {
        when(reviewService.getMyReviews("buyer", ReviewSort.PRICE, 2, 5))
                .thenReturn(new ReviewPageResponseDto(List.of(), 2, 5, 0, 0));
        when(reviewService.getArtistReviews(
                "seller", 10L, ReviewSort.STAR, 1, 6))
                .thenReturn(new ArtistReviewPageResponseDto(
                        List.of(), 1, 6, 0, 0, 3, 4, null, List.of(),
                        3, 2, 1, List.of()
                ));

        mockMvc.perform(get("/api/users/me/reviews")
                        .param("sort", "PRICE")
                        .param("page", "2")
                        .param("size", "5")
                        .with(authentication(user("buyer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2));

        mockMvc.perform(get("/api/artists/me/reviews")
                        .param("artId", "10")
                        .param("sort", "STAR")
                        .param("page", "1")
                        .with(authentication(artist("seller"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReviewCount").value(3))
                .andExpect(jsonPath("$.endedArtCount").value(4))
                .andExpect(jsonPath("$.soldArtCount").value(3))
                .andExpect(jsonPath("$.reviewedArtCount").value(2))
                .andExpect(jsonPath("$.unreviewedArtCount").value(1));

        verify(reviewService).getMyReviews("buyer", ReviewSort.PRICE, 2, 5);
        verify(reviewService).getArtistReviews(
                "seller", 10L, ReviewSort.STAR, 1, 6);
    }

    @Test
    void authenticationAndArtistRoleAreEnforced() throws Exception {
        mockMvc.perform(get("/api/users/me/reviews"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/artists/me/reviews")
                        .with(authentication(user("buyer"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void invalidSortReturnsStructuredBadRequest() throws Exception {
        mockMvc.perform(get("/api/users/me/reviews")
                        .param("sort", "UNKNOWN")
                        .with(authentication(user("buyer"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void unexpectedErrorDoesNotExposeImplementationDetails() throws Exception {
        when(reviewService.getMyReviews(
                "buyer", ReviewSort.RECENT, 0, 6))
                .thenThrow(new IllegalStateException("database-password-secret"));

        mockMvc.perform(get("/api/users/me/reviews")
                        .with(authentication(user("buyer"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."));
    }

    private ReviewResponseDto review(Long reviewId, Long orderId) {
        return new ReviewResponseDto(
                reviewId,
                orderId,
                5L,
                "작품",
                "https://example.com/art.jpg",
                "작가",
                "buyer",
                100_000,
                9,
                "충분히 긴 리뷰 내용입니다",
                LocalDateTime.of(2026, 8, 26, 12, 0),
                LocalDateTime.of(2026, 8, 26, 12, 0)
        );
    }

    private UsernamePasswordAuthenticationToken user(String userId) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    private UsernamePasswordAuthenticationToken artist(String userId) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ARTIST"))
        );
    }
}
