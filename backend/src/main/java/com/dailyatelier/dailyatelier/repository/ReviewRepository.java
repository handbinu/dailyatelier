package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByArt(Art art);

    boolean existsByOrderOrderId(Long orderId);

    Optional<Review> findByOrderOrderId(Long orderId);

    @Query("select review.reviewId from Review review where review.order.orderId = :orderId")
    Optional<Long> findReviewIdByOrderId(@Param("orderId") Long orderId);

    Page<Review> findByUserUserId(String userId, Pageable pageable);

    @Query("""
            select review
            from Review review
            where review.art.artist.user.userId = :artistUserId
              and (:artId is null or review.art.artId = :artId)
            """)
    Page<Review> findArtistReviews(
            @Param("artistUserId") String artistUserId,
            @Param("artId") Long artId,
            Pageable pageable);

    @Query("""
            select count(review)
            from Review review
            where review.art.artist.user.userId = :artistUserId
            """)
    long countArtistReviews(@Param("artistUserId") String artistUserId);

    @Query("""
            select count(distinct review.art.artId)
            from Review review
            where review.art.artist.user.userId = :artistUserId
              and review.art.artStatus = :soldStatus
            """)
    long countReviewedSoldArts(
            @Param("artistUserId") String artistUserId,
            @Param("soldStatus") Integer soldStatus);

    @Query("""
            select avg(review.star)
            from Review review
            where review.art.artist.user.userId = :artistUserId
              and (:artId is null or review.art.artId = :artId)
            """)
    Double averageArtistStar(
            @Param("artistUserId") String artistUserId,
            @Param("artId") Long artId);
}
