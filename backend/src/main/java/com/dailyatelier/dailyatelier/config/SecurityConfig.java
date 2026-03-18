package com.dailyatelier.dailyatelier.config;

import com.dailyatelier.dailyatelier.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws  Exception{
        http
            .userDetailsService(customUserDetailsService)
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/register", "login").permitAll()
                    .anyRequest().authenticated()
            )
            .formLogin(form -> form
                    .loginPage("/login")
                    .defaultSuccessUrl("/")
                    .permitAll()
            )
            .logout(logout -> logout
                    .logoutSuccessUrl("/login")
                    .permitAll()
            );
        return http.build();
    }
}