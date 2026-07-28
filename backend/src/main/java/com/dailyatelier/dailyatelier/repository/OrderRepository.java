package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.Order;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByArtArtId(Long artId);

    boolean existsByArtArtId(Long artId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select orders from Order orders where orders.orderId = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);
}
