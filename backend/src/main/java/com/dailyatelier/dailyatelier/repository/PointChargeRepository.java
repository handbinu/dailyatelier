package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.PaymentProvider;
import com.dailyatelier.dailyatelier.entity.PointCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface PointChargeRepository extends JpaRepository<PointCharge, Long> {
    Optional<PointCharge> findByMerchantOrderId(String merchantOrderId);

    Optional<PointCharge> findByProviderAndPgOrderId(
            PaymentProvider provider,
            String pgOrderId);

    Optional<PointCharge> findByUserIdAndIdempotencyKey(
            String userId,
            String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select charge from PointCharge charge where charge.chargeId = :chargeId")
    Optional<PointCharge> findByIdForUpdate(@Param("chargeId") Long chargeId);
}
