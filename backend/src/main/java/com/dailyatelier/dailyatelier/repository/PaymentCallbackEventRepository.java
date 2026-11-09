package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.PaymentCallbackEvent;
import com.dailyatelier.dailyatelier.entity.PaymentProvider;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentCallbackEventRepository extends JpaRepository<PaymentCallbackEvent, Long> {
    Optional<PaymentCallbackEvent> findByProviderAndProviderEventId(
            PaymentProvider provider, String providerEventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event from PaymentCallbackEvent event
            where event.provider = :provider and event.providerEventId = :providerEventId
            """)
    Optional<PaymentCallbackEvent> findForUpdate(
            @Param("provider") PaymentProvider provider,
            @Param("providerEventId") String providerEventId);
}
