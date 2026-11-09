package com.dailyatelier.dailyatelier.jwt;

import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private UserRepository userRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesUsingCurrentDatabaseRoleWhenVersionMatches() throws Exception {
        User user = user("member", 2, 4);
        authenticate("token", "member", 4, user);

        filter().doFilter(request("token"), new MockHttpServletResponse(), (request, response) -> { });

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("member");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void rejectsExistingTokenAfterAuthenticationVersionChanges() throws Exception {
        authenticate("token", "member", 0, user("member", 1, 1));

        filter().doFilter(request("token"), new MockHttpServletResponse(), (request, response) -> { });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsLegacyTokenWithoutAuthenticationVersion() throws Exception {
        when(tokenProvider.validateToken("token")).thenReturn(true);
        when(tokenProvider.getUserId("token")).thenReturn("member");
        when(tokenProvider.getAuthVersion("token")).thenReturn(null);

        filter().doFilter(request("token"), new MockHttpServletResponse(), (request, response) -> { });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private void authenticate(String token, String userId, int tokenAuthVersion, User user) {
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getUserId(token)).thenReturn(userId);
        when(tokenProvider.getAuthVersion(token)).thenReturn(tokenAuthVersion);
        when(userRepository.findByUserId(userId)).thenReturn(user);
    }

    private JwtAuthenticationFilter filter() {
        return new JwtAuthenticationFilter(tokenProvider, userRepository);
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private User user(String userId, int userStatus, int authVersion) {
        User user = new User();
        user.setUserId(userId);
        user.setUserStatus(userStatus);
        user.setAuthVersion(authVersion);
        return user;
    }
}
