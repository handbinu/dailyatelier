package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.PointTransaction;
import com.dailyatelier.dailyatelier.entity.PointReferenceType;
import com.dailyatelier.dailyatelier.entity.PointTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    long countByUserId(String userId);

    Page<PointTransaction> findByUserIdOrderByCreatedAtDescTransactionIdDesc(
            String userId,
            Pageable pageable);

    java.util.Optional<PointTransaction> findByReferenceTypeAndReferenceIdAndType(
            PointReferenceType referenceType,
            String referenceId,
            PointTransactionType type);

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

    @Query(value = """
            select
                account.user_id as userId,
                account.available_balance as accountAvailableBalance,
                account.held_balance as accountHeldBalance,
                coalesce(sum(transaction.available_delta), 0) as ledgerAvailableBalance,
                coalesce(sum(transaction.held_delta), 0) as ledgerHeldBalance
            from point_account account
            left join point_transaction transaction
                on transaction.user_id = account.user_id
            group by
                account.user_id,
                account.available_balance,
                account.held_balance
            having account.available_balance <> coalesce(sum(transaction.available_delta), 0)
                or account.held_balance <> coalesce(sum(transaction.held_delta), 0)
            order by account.user_id
            """, nativeQuery = true)
    List<PointLedgerMismatch> findLedgerMismatches();

    interface PointLedgerMismatch {
        String getUserId();
        long getAccountAvailableBalance();
        long getAccountHeldBalance();
        long getLedgerAvailableBalance();
        long getLedgerHeldBalance();
    }
}
