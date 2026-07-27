package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.dto.BidSummaryQueryDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Bid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {

    Optional<Bid> findFirstByArtOrderByBidPriceDescBidTimeAscBidIdAsc(Art art);

    @Query(
            value = """
                    select new com.dailyatelier.dailyatelier.dto.BidSummaryQueryDto(
                        art.artId,
                        art.name,
                        artist.artistName,
                        art.imgPath,
                        max(bid.bidPrice),
                        art.currentPrice,
                        max(bid.bidTime),
                        art.bidStartTime,
                        art.closingTime,
                        art.artStatus,
                        winningBid.user.userId
                    )
                    from Bid bid
                    join bid.art art
                    join art.artist artist
                    left join art.winningBid winningBid
                    where bid.user.userId = :userId
                    group by
                        art.artId,
                        art.name,
                        artist.artistName,
                        art.imgPath,
                        art.currentPrice,
                        art.bidStartTime,
                        art.closingTime,
                        art.artStatus,
                        winningBid.user.userId
                    order by max(bid.bidTime) desc, art.artId desc
                    """,
            countQuery = """
                    select count(distinct bid.art.artId)
                    from Bid bid
                    where bid.user.userId = :userId
                    """
    )
    Page<BidSummaryQueryDto> findBidSummariesByUserId(
            @Param("userId") String userId,
            Pageable pageable);
}
