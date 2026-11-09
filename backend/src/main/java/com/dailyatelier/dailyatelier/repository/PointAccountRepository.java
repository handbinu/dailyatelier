package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.PointAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PointAccountRepository extends JpaRepository<PointAccount, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from PointAccount account where account.userId = :userId")
    Optional<PointAccount> findByUserIdForUpdate(@Param("userId") String userId);
}
