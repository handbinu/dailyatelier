package com.dailyatelier.dailyatelier.config;

import com.dailyatelier.dailyatelier.dto.ApiErrorResponseDto;
import com.dailyatelier.dailyatelier.jwt.JwtAuthenticationFilter;
import com.dailyatelier.dailyatelier.jwt.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;


@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws  Exception{
        http
                //CSRF 비활성화
                .csrf(AbstractHttpConfigurer::disable)
                //CORS 설정 적용
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                //세션 사용 X
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            objectMapper.writeValue(response.getWriter(), ApiErrorResponseDto.of(
                                    HttpStatus.UNAUTHORIZED.value(),
                                    "UNAUTHORIZED",
                                    "인증이 필요합니다.",
                                    request.getRequestURI()
                            ));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            objectMapper.writeValue(response.getWriter(), ApiErrorResponseDto.of(
                                    HttpStatus.FORBIDDEN.value(),
                                    "FORBIDDEN",
                                    "접근 권한이 없습니다.",
                                    request.getRequestURI()
                            ));
                        })
                )
                //요청 권한 설정
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                        .requestMatchers("/api/check/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/arts", "/api/arts/search", "/api/arts/*").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/artists",
                                "/api/artists/{artistId}",
                                "/api/artists/{artistId}/arts").permitAll()
                        .requestMatchers("/api/artists/**").hasRole("ARTIST")
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
                //JWT 필터를  UsernamePasswordAuthenticationFilter 앞에 추가
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    // CORS 설정 - React(5173)에서 Spring(8080)으로 요청 허용
    @Bean
    public CorsConfigurationSource corsConfigurationSource(){
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));     //React 개발 서버
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return  source;
    }
}
