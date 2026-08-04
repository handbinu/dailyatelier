package com.dailyatelier.dailyatelier.controller;

import com.dailyatelier.dailyatelier.config.SecurityConfig;
import com.dailyatelier.dailyatelier.exception.DomainApiException;
import com.dailyatelier.dailyatelier.jwt.JwtTokenProvider;
import com.dailyatelier.dailyatelier.service.ArtService;
import com.dailyatelier.dailyatelier.service.UserService;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private ArtService artService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void loginFailureUsesCommonErrorContract() throws Exception {
        when(userService.login(any())).thenThrow(new DomainApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "아이디 또는 비밀번호가 올바르지 않습니다"
        ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"userId\":\"user\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message")
                        .value("아이디 또는 비밀번호가 올바르지 않습니다"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    @Test
    void missingProfileUsesCommonErrorContract() throws Exception {
        when(userService.getUserProfile("missing-user")).thenThrow(new DomainApiException(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "사용자를 찾을 수 없습니다"
        ));

        mockMvc.perform(get("/api/users/me")
                        .with(authentication(authToken("missing-user"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다"))
                .andExpect(jsonPath("$.path").value("/api/users/me"));
    }

    @Test
    void unreadableRequestDoesNotBecomeInternalServerError() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("요청값을 확인해 주세요."))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    private UsernamePasswordAuthenticationToken authToken(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}
