package com.dailyatelier.dailyatelier.jwt;

import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            String userId = jwtTokenProvider.getUserId(token);
            Integer tokenAuthVersion = jwtTokenProvider.getAuthVersion(token);
            User user = userRepository.findByUserId(userId);

            if (user != null
                    && tokenAuthVersion != null
                    && Objects.equals(tokenAuthVersion, user.getAuthVersion())) {
                authenticate(userId, user.getUserStatus());
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String userId, Integer userStatus) {
        String role = switch (userStatus == null ? 0 : userStatus) {
            case 2 -> "ROLE_ADMIN";
            case 1 -> "ROLE_ARTIST";
            default -> "ROLE_USER";
        };

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(new SimpleGrantedAuthority(role))
                );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    //Authorization: Bearer {token} 에서 코드 추출
    private String resolveToken(HttpServletRequest request){
        String bearerToken = request.getHeader("Authorization");
        if(StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")){
            return bearerToken.substring(7);
        }
        return null;
    }

}
