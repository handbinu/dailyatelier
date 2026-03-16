package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public void register(User user){
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setJoinDate(LocalDateTime.now());
        user.setReserve(0);
        userRepository.save(user);
    }

    public boolean isUserIdDuplicate(String userId){
        return userRepository.existsByUserId(userId);
    }
    public boolean isNicknameDuplicate(String nickname) {
        return userRepository.existsByNickname(nickname);
    }
}
