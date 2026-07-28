package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.Order;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByArtArtId(Long artId);

    boolean existsByArtArtId(Long artId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select orders from Order orders where orders.orderId = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);

    @Query("""
            select orders.orderId
            from Order orders
            where orders.status = :status
              and orders.paymentDueAt <= :now
            order by orders.paymentDueAt asc, orders.orderId asc
            """)
    List<Long> findPaymentExpiredOrderIds(
            @Param("status") com.dailyatelier.dailyatelier.entity.OrderStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    Page<Order> findByBuyer_UserId(String userId, Pageable pageable);

    Page<Order> findByBuyer_UserIdAndStatus(
            String userId,
            com.dailyatelier.dailyatelier.entity.OrderStatus status,
            Pageable pageable);

    Page<Order> findBySeller_UserId(String userId, Pageable pageable);

    Page<Order> findBySeller_UserIdAndStatus(
            String userId,
            com.dailyatelier.dailyatelier.entity.OrderStatus status,
            Pageable pageable);

    @Query("""
            select orders.status as status, count(orders) as total
            from Order orders
            where orders.buyer.userId = :userId
            group by orders.status
            """)
    List<OrderStatusCountProjection> countByBuyerUserIdGrouped(
            @Param("userId") String userId);

    @Query("""
            select orders.status as status, count(orders) as total
            from Order orders
            where orders.seller.userId = :userId
            group by orders.status
            """)
    List<OrderStatusCountProjection> countBySellerUserIdGrouped(
            @Param("userId") String userId);

    interface OrderStatusCountProjection {
        com.dailyatelier.dailyatelier.entity.OrderStatus getStatus();

        long getTotal();
    }
}
