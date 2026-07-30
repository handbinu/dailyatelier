package com.dailyatelier.dailyatelier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "point_charge",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_point_charge_merchant_order",
                        columnNames = "merchant_order_id"
                ),
                @UniqueConstraint(
                        name = "uq_point_charge_provider_pg_order",
                        columnNames = {"provider", "pg_order_id"}
                ),
                @UniqueConstraint(
                        name = "uq_point_charge_user_idempotency",
                        columnNames = {"user_id", "idempotency_key"}
                )
        }
)
@Check(constraints = "requested_amount > 0 and paid_amount >= 0")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "charge_id")
    private Long chargeId;

    @Column(name = "user_id", nullable = false, length = 45)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentProvider provider;

    @Column(name = "merchant_order_id", nullable = false, length = 100)
    private String merchantOrderId;

    @Column(name = "pg_order_id", length = 100)
    private String pgOrderId;

    @Column(name = "requested_amount", nullable = false)
    private long requestedAmount;

    @Column(name = "paid_amount", nullable = false)
    private long paidAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointChargeStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 150)
    private String idempotencyKey;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "charge_transaction_id")
    private Long chargeTransactionId;

    @Column(name = "refund_transaction_id")
    private Long refundTransactionId;

    public static PointCharge pending(
            String userId,
            PaymentProvider provider,
            String merchantOrderId,
            long requestedAmount,
            String idempotencyKey,
            LocalDateTime createdAt) {
        if (requestedAmount <= 0) {
            throw new IllegalArgumentException("충전 요청 금액은 양수여야 합니다");
        }
        PointCharge charge = new PointCharge();
        charge.userId = requireText(userId, "사용자 ID");
        charge.provider = Objects.requireNonNull(provider, "결제 제공자는 필수입니다");
        charge.merchantOrderId = requireText(merchantOrderId, "가맹점 주문번호");
        charge.requestedAmount = requestedAmount;
        charge.paidAmount = 0L;
        charge.status = PointChargeStatus.PENDING;
        charge.idempotencyKey = requireText(idempotencyKey, "멱등성 키");
        charge.createdAt = Objects.requireNonNull(createdAt, "충전 생성 시각은 필수입니다");
        return charge;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 필수입니다");
        }
        return value;
    }
}
