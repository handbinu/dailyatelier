package com.dailyatelier.dailyatelier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "point_hold",
        indexes = {
                @Index(name = "idx_point_hold_art_created", columnList = "art_id, created_at"),
                @Index(name = "idx_point_hold_user_status_created", columnList = "user_id, status, created_at")
        }
)
@Check(constraints = "amount > 0")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hold_id")
    private Long holdId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "art_id", nullable = false)
    private Art art;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "latest_bid_id", nullable = false)
    private Bid latestBid;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointHoldStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "committed_at")
    private LocalDateTime committedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "release_reason", length = 30)
    private PointHoldReleaseReason releaseReason;

    @Column(name = "commit_order_id")
    private Long commitOrderId;

    public static PointHold hold(
            Art art,
            User user,
            Bid latestBid,
            long amount,
            LocalDateTime createdAt) {
        if (amount <= 0) {
            throw new IllegalArgumentException("예치 금액은 양수여야 합니다");
        }
        PointHold hold = new PointHold();
        hold.art = Objects.requireNonNull(art, "작품은 필수입니다");
        hold.user = Objects.requireNonNull(user, "사용자는 필수입니다");
        hold.latestBid = Objects.requireNonNull(latestBid, "최신 입찰은 필수입니다");
        hold.amount = amount;
        hold.status = PointHoldStatus.HELD;
        hold.createdAt = Objects.requireNonNull(createdAt, "예치 생성 시각은 필수입니다");
        hold.updatedAt = createdAt;
        return hold;
    }

    public void increase(Bid latestBid, long amount, LocalDateTime updatedAt) {
        requireHeld();
        if (amount <= 0) {
            throw new IllegalArgumentException("추가 예치 금액은 양수여야 합니다");
        }
        this.latestBid = Objects.requireNonNull(latestBid, "최신 입찰은 필수입니다");
        this.amount = Math.addExact(this.amount, amount);
        this.updatedAt = Objects.requireNonNull(updatedAt, "변경 시각은 필수입니다");
    }

    public void release(PointHoldReleaseReason reason, LocalDateTime releasedAt) {
        requireHeld();
        this.status = PointHoldStatus.RELEASED;
        this.releaseReason = Objects.requireNonNull(reason, "해제 사유는 필수입니다");
        this.releasedAt = Objects.requireNonNull(releasedAt, "해제 시각은 필수입니다");
        this.updatedAt = releasedAt;
    }

    private void requireHeld() {
        if (this.status != PointHoldStatus.HELD) {
            throw new IllegalStateException("활성 예치만 변경할 수 있습니다");
        }
    }
}
