package com.dailyatelier.dailyatelier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "point_account")
@Check(constraints = "available_balance >= 0 and held_balance >= 0")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointAccount {

    @Id
    @Column(name = "user_id", length = 45)
    private String userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "available_balance", nullable = false)
    private long availableBalance;

    @Column(name = "held_balance", nullable = false)
    private long heldBalance;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static PointAccount open(
            User user,
            long openingBalance,
            LocalDateTime openedAt) {
        if (openingBalance < 0) {
            throw new IllegalArgumentException("개시 잔액은 음수일 수 없습니다");
        }
        PointAccount account = new PointAccount();
        account.user = Objects.requireNonNull(user, "사용자는 필수입니다");
        account.userId = Objects.requireNonNull(user.getUserId(), "사용자 ID는 필수입니다");
        account.availableBalance = openingBalance;
        account.heldBalance = 0L;
        account.createdAt = Objects.requireNonNull(openedAt, "계정 생성 시각은 필수입니다");
        account.updatedAt = openedAt;
        return account;
    }

    public void credit(long amount, LocalDateTime updatedAt) {
        if (amount <= 0) {
            throw new IllegalArgumentException("적립 금액은 양수여야 합니다");
        }
        this.availableBalance = Math.addExact(this.availableBalance, amount);
        this.updatedAt = Objects.requireNonNull(updatedAt, "변경 시각은 필수입니다");
    }

    public void debit(long amount, LocalDateTime updatedAt) {
        if (amount <= 0) {
            throw new IllegalArgumentException("차감 금액은 양수여야 합니다");
        }
        if (this.availableBalance < amount) {
            throw new IllegalStateException("사용 가능 포인트가 부족합니다");
        }
        this.availableBalance -= amount;
        this.updatedAt = Objects.requireNonNull(updatedAt, "변경 시각은 필수입니다");
    }

    public void hold(long amount, LocalDateTime updatedAt) {
        debit(amount, updatedAt);
        this.heldBalance = Math.addExact(this.heldBalance, amount);
    }

    public void release(long amount, LocalDateTime updatedAt) {
        if (amount <= 0) {
            throw new IllegalArgumentException("해제 금액은 양수여야 합니다");
        }
        if (this.heldBalance < amount) {
            throw new IllegalStateException("예치 포인트가 부족합니다");
        }
        this.heldBalance -= amount;
        this.availableBalance = Math.addExact(this.availableBalance, amount);
        this.updatedAt = Objects.requireNonNull(updatedAt, "변경 시각은 필수입니다");
    }

    public void commit(long amount, LocalDateTime updatedAt) {
        if (amount <= 0) {
            throw new IllegalArgumentException("확정 금액은 양수여야 합니다");
        }
        if (this.heldBalance < amount) {
            throw new IllegalStateException("예치 포인트가 부족합니다");
        }
        this.heldBalance -= amount;
        this.updatedAt = Objects.requireNonNull(updatedAt, "변경 시각은 필수입니다");
    }
}
