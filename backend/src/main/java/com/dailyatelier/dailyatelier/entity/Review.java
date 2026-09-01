package com.dailyatelier.dailyatelier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "review",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_review_order",
                columnNames = "order_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reviewId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "art_id", nullable = false)
    private Art art;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(nullable = false)
    private Integer star;

    @Column(nullable = false, length = 300)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Review create(
            Order order,
            int star,
            String content,
            LocalDateTime createdAt) {
        Objects.requireNonNull(order, "주문은 필수입니다");
        Objects.requireNonNull(order.getBuyer(), "주문 구매자는 필수입니다");
        Objects.requireNonNull(order.getArt(), "주문 작품은 필수입니다");

        Review review = new Review();
        review.order = order;
        review.user = order.getBuyer();
        review.art = order.getArt();
        review.star = validateStar(star);
        review.content = normalizeContent(content);
        review.createdAt = Objects.requireNonNull(createdAt, "작성 시각은 필수입니다");
        review.updatedAt = createdAt;
        return review;
    }

    public void update(int star, String content, LocalDateTime updatedAt) {
        this.star = validateStar(star);
        this.content = normalizeContent(content);
        this.updatedAt = Objects.requireNonNull(updatedAt, "수정 시각은 필수입니다");
    }

    private static int validateStar(int star) {
        if (star < 1 || star > 10) {
            throw new IllegalArgumentException("별점은 1에서 10 사이여야 합니다");
        }
        return star;
    }

    private static String normalizeContent(String content) {
        if (content == null) {
            throw new IllegalArgumentException("리뷰 내용은 필수입니다");
        }
        String normalized = content.trim();
        if (normalized.length() < 10 || normalized.length() > 300) {
            throw new IllegalArgumentException("리뷰 내용은 10자 이상 300자 이하여야 합니다");
        }
        return normalized;
    }
}
