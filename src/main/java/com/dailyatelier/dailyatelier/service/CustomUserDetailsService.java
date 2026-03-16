package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        User user = userRepository.findByUserId(userId);
        if(user == null ){
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + userId);
        }
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUserId())
                .password(user.getPassword())
                .roles(user.getUserStatus() == 2 ? "ADMIN" : user.getUserStatus() == 1 ? "ARTIST" : "USER")
                .build();
    }
}
