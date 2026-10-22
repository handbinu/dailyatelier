package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    boolean existsByUserId(String userId);
    boolean existsByNickname(String nickname);
    boolean existsByProfileImagePublicId(String profileImagePublicId);
    User findByUserId(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.userId = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") String userId);
}
