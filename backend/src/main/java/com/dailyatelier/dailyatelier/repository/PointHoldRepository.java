package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.PointHold;
import com.dailyatelier.dailyatelier.entity.PointHoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PointHoldRepository extends JpaRepository<PointHold, Long> {
    List<PointHold> findByUser_UserIdAndStatusOrderByCreatedAtDesc(
            String userId,
            PointHoldStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select hold from PointHold hold where hold.holdId = :holdId")
    Optional<PointHold> findByIdForUpdate(@Param("holdId") Long holdId);
}
