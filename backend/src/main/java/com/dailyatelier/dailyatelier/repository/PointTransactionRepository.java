package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.PointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    long countByUserId(String userId);

    @Query("""
            select coalesce(sum(transaction.availableDelta), 0)
            from PointTransaction transaction
            where transaction.userId = :userId
            """)
    long sumAvailableDeltaByUserId(@Param("userId") String userId);

    @Query("""
            select coalesce(sum(transaction.heldDelta), 0)
            from PointTransaction transaction
            where transaction.userId = :userId
            """)
    long sumHeldDeltaByUserId(@Param("userId") String userId);
}
