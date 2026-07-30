package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.PointHold;
import com.dailyatelier.dailyatelier.entity.PointHoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointHoldRepository extends JpaRepository<PointHold, Long> {
    List<PointHold> findByUser_UserIdAndStatusOrderByCreatedAtDesc(
            String userId,
            PointHoldStatus status);
}
