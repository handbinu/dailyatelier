package com.dailyatelier.dailyatelier.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewTest {
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 26, 12, 0);

    @Test
    void createsFromOrderRelationsAndNormalizesContent() {
        User buyer = new User();
        Art art = new Art();
        Order order = mock(Order.class);
        when(order.getBuyer()).thenReturn(buyer);
        when(order.getArt()).thenReturn(art);

        Review review = Review.create(
                order,
                8,
                "  충분히 만족스러운 작품입니다  ",
                NOW
        );

        assertThat(review.getOrder()).isSameAs(order);
        assertThat(review.getUser()).isSameAs(buyer);
        assertThat(review.getArt()).isSameAs(art);
        assertThat(review.getStar()).isEqualTo(8);
        assertThat(review.getContent()).isEqualTo("충분히 만족스러운 작품입니다");
        assertThat(review.getCreatedAt()).isEqualTo(NOW);
        assertThat(review.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsInvalidStarAndTrimmedContentLength() {
        Order order = validOrder();

        assertThatThrownBy(() -> Review.create(order, 0, "충분히 긴 리뷰 내용입니다", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("별점");
        assertThatThrownBy(() -> Review.create(order, 11, "충분히 긴 리뷰 내용입니다", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("별점");
        assertThatThrownBy(() -> Review.create(order, 5, "         짧음         ", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10자");
    }

    @Test
    void updateKeepsCreatedAtAndChangesUpdatedAt() {
        Review review = Review.create(
                validOrder(),
                5,
                "처음 작성한 충분히 긴 리뷰",
                NOW
        );

        review.update(10, "  수정한 충분히 긴 리뷰 내용  ", NOW.plusDays(1));

        assertThat(review.getStar()).isEqualTo(10);
        assertThat(review.getContent()).isEqualTo("수정한 충분히 긴 리뷰 내용");
        assertThat(review.getCreatedAt()).isEqualTo(NOW);
        assertThat(review.getUpdatedAt()).isEqualTo(NOW.plusDays(1));
    }

    private Order validOrder() {
        Order order = mock(Order.class);
        when(order.getBuyer()).thenReturn(new User());
        when(order.getArt()).thenReturn(new Art());
        return order;
    }
}
