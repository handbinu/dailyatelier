package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.config.SecurityConfig;
import com.dailyatelier.dailyatelier.exception.DomainApiException;
import com.dailyatelier.dailyatelier.jwt.JwtTokenProvider;
import com.dailyatelier.dailyatelier.service.LikesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LikesController.class)
@Import(SecurityConfig.class)
class LikesApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LikesService likesService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void missingUserLikeListUsesCommonNotFoundContract() throws Exception {
        when(likesService.getMyLikes(eq("missing-user"), any()))
                .thenThrow(userNotFound());

        mockMvc.perform(get("/api/users/me/likes")
                        .with(authentication(authToken("missing-user"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.path").value("/api/users/me/likes"));
    }

    @Test
    void missingArtLikeStatusUsesCommonNotFoundContract() throws Exception {
        when(likesService.getLikeStatus(eq("member"), eq(999L)))
                .thenThrow(artNotFound());

        mockMvc.perform(get("/api/arts/999/like")
                        .with(authentication(authToken("member"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("ART_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("작품을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.path").value("/api/arts/999/like"));
    }

    @Test
    void missingArtAddLikeUsesCommonNotFoundContract() throws Exception {
        when(likesService.addLike(eq("member"), eq(999L)))
                .thenThrow(artNotFound());

        mockMvc.perform(post("/api/arts/999/like")
                        .with(authentication(authToken("member"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("ART_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/arts/999/like"));
    }

    @Test
    void missingUserRemoveLikeUsesCommonNotFoundContract() throws Exception {
        when(likesService.removeLike(eq("missing-user"), eq(999L)))
                .thenThrow(userNotFound());

        mockMvc.perform(delete("/api/arts/999/like")
                        .with(authentication(authToken("missing-user"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/arts/999/like"));
    }

    private DomainApiException userNotFound() {
        return new DomainApiException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "사용자를 찾을 수 없습니다."
        );
    }

    private DomainApiException artNotFound() {
        return new DomainApiException(
                HttpStatus.NOT_FOUND,
                "ART_NOT_FOUND",
                "작품을 찾을 수 없습니다."
        );
    }

    private UsernamePasswordAuthenticationToken authToken(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}
