package com.dailyatelier.dailyatelier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Immutable
@Table(
        name = "point_transaction",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_point_transaction_idempotency",
                        columnNames = "idempotency_key"
                ),
                @UniqueConstraint(
                        name = "uq_point_transaction_reversal_type",
                        columnNames = {"reversal_of_transaction_id", "type"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_point_transaction_user_created",
                        columnList = "user_id, created_at, transaction_id"
                ),
                @Index(
                        name = "idx_point_transaction_reference",
                        columnList = "reference_type, reference_id, type"
                )
        }
)
@Check(constraints = "amount > 0 and available_balance_after >= 0 and held_balance_after >= 0")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "user_id", nullable = false, length = 45)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PointTransactionType type;

    @Column(nullable = false)
    private long amount;

    @Column(name = "available_delta", nullable = false)
    private long availableDelta;

    @Column(name = "held_delta", nullable = false)
    private long heldDelta;

    @Column(name = "available_balance_after", nullable = false)
    private long availableBalanceAfter;

    @Column(name = "held_balance_after", nullable = false)
    private long heldBalanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 30)
    private PointReferenceType referenceType;

    @Column(name = "reference_id", nullable = false, length = 100)
    private String referenceId;

    @Column(name = "idempotency_key", nullable = false, length = 150)
    private String idempotencyKey;

    @Column(name = "reversal_of_transaction_id")
    private Long reversalOfTransactionId;

    @Column(name = "reason_code", length = 50)
    private String reasonCode;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static PointTransaction openingBalance(
            String userId,
            long balance,
            LocalDateTime createdAt) {
        return record(
                userId,
                PointTransactionType.OPENING_BALANCE,
                balance,
                balance,
                0L,
                balance,
                0L,
                PointReferenceType.USER,
                userId,
                "opening-balance:" + userId,
                null,
                "LEGACY_RESERVE_MIGRATION",
                "기존 보유 포인트 이관",
                createdAt
        );
    }

    public static PointTransaction record(
            String userId,
            PointTransactionType type,
            long amount,
            long availableDelta,
            long heldDelta,
            long availableBalanceAfter,
            long heldBalanceAfter,
            PointReferenceType referenceType,
            String referenceId,
            String idempotencyKey,
            Long reversalOfTransactionId,
            String reasonCode,
            String description,
            LocalDateTime createdAt) {
        if (amount <= 0) {
            throw new IllegalArgumentException("거래 금액은 양수여야 합니다");
        }
        if (availableBalanceAfter < 0 || heldBalanceAfter < 0) {
            throw new IllegalArgumentException("거래 후 잔액은 음수일 수 없습니다");
        }
        PointTransaction transaction = new PointTransaction();
        transaction.userId = requireText(userId, "사용자 ID");
        transaction.type = Objects.requireNonNull(type, "거래 유형은 필수입니다");
        transaction.amount = amount;
        transaction.availableDelta = availableDelta;
        transaction.heldDelta = heldDelta;
        transaction.availableBalanceAfter = availableBalanceAfter;
        transaction.heldBalanceAfter = heldBalanceAfter;
        transaction.referenceType = Objects.requireNonNull(referenceType, "참조 유형은 필수입니다");
        transaction.referenceId = requireText(referenceId, "참조 ID");
        transaction.idempotencyKey = requireText(idempotencyKey, "멱등성 키");
        transaction.reversalOfTransactionId = reversalOfTransactionId;
        transaction.reasonCode = reasonCode;
        transaction.description = description;
        transaction.createdAt = Objects.requireNonNull(createdAt, "거래 시각은 필수입니다");
        return transaction;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 필수입니다");
        }
        return value;
    }
}
