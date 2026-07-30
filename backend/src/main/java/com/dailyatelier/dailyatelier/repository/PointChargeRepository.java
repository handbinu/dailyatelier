package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.PaymentProvider;
import com.dailyatelier.dailyatelier.entity.PointCharge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointChargeRepository extends JpaRepository<PointCharge, Long> {
    Optional<PointCharge> findByMerchantOrderId(String merchantOrderId);

    Optional<PointCharge> findByProviderAndPgOrderId(
            PaymentProvider provider,
            String pgOrderId);

    Optional<PointCharge> findByUserIdAndIdempotencyKey(
            String userId,
            String idempotencyKey);
}
