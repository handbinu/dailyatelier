package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
    boolean existsByUserId(String userId);
    boolean existsByNickname(String nickname);
    User findByUserId(String userId);
}
