package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.dto.MyArtQueryDto;
import com.dailyatelier.dailyatelier.dto.WinningArtResponseDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

public interface ArtRepository extends JpaRepository<Art, Long>, ArtSearchRepository {
    Page<Art> findByArtStatus(Integer artStatus, Pageable pageable);

    Page<Art> findByArtist(Artist artist, Pageable pageable);

    @Query(
            value = """
                    select new com.dailyatelier.dailyatelier.dto.ArtResponseDto(
                        art.artId,
                        artist.artistCode,
                        artist.artistName,
                        art.name,
                        art.descript,
                        art.material,
                        art.wIntro,
                        art.startPrice,
                        art.currentPrice,
                        art.bidStartTime,
                        art.closingTime,
                        art.imgPath,
                        art.artStatus
                    )
                    from Art art
                    join art.artist artist
                    where artist.artistCode = :artistId
                      and art.artStatus in :publicStatuses
                    order by art.closingTime asc, art.artId desc
                    """,
            countQuery = """
                    select count(art)
                    from Art art
                    join art.artist artist
                    where artist.artistCode = :artistId
                      and art.artStatus in :publicStatuses
                    """
    )
    Page<com.dailyatelier.dailyatelier.dto.ArtResponseDto> findPublicArtsByArtistId(
            @Param("artistId") String artistId,
            @Param("publicStatuses") List<Integer> publicStatuses,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select art from Art art where art.artId = :artId")
    Optional<Art> findByIdForUpdate(@Param("artId") Long artId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select art from Art art where art.artId = :artId")
    Optional<Art> findByIdForClosing(@Param("artId") Long artId);

    @Query("""
            select art.artId
            from Art art
            where art.artStatus = :activeStatus
              and art.closingTime <= :now
            order by art.closingTime asc, art.artId asc
            """)
    List<Long> findExpiredActiveArtIds(
            @Param("activeStatus") Integer activeStatus,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Query(
            value = """
                    select new com.dailyatelier.dailyatelier.dto.WinningArtResponseDto(
                        art.artId,
                        art.name,
                        artist.artistName,
                        art.imgPath,
                        winningBid.bidPrice,
                        art.closedAt
                    )
                    from Art art
                    join art.artist artist
                    join art.winningBid winningBid
                    where art.artStatus = :soldStatus
                      and winningBid.user.userId = :userId
                    order by art.closedAt desc, art.artId desc
                    """,
            countQuery = """
                    select count(art)
                    from Art art
                    join art.winningBid winningBid
                    where art.artStatus = :soldStatus
                      and winningBid.user.userId = :userId
                    """
    )
    Page<WinningArtResponseDto> findWinningArtsByUserId(
            @Param("userId") String userId,
            @Param("soldStatus") Integer soldStatus,
            Pageable pageable);

    @Query(
            value = """
                    select new com.dailyatelier.dailyatelier.dto.MyArtQueryDto(
                        art.artId,
                        artist.artistCode,
                        artist.artistName,
                        art.name,
                        art.descript,
                        art.material,
                        art.wIntro,
                        art.startPrice,
                        art.currentPrice,
                        art.bidStartTime,
                        art.closingTime,
                        art.imgPath,
                        art.artStatus,
                        art.closedAt,
                        winningBid.bidPrice,
                        count(allBid)
                    )
                    from Art art
                    join art.artist artist
                    left join art.winningBid winningBid
                    left join Bid allBid on allBid.art = art
                    where artist.artistCode = :artistCode
                      and art.artStatus in :artStatuses
                    group by
                        art.artId,
                        artist.artistCode,
                        artist.artistName,
                        art.name,
                        art.descript,
                        art.material,
                        art.wIntro,
                        art.startPrice,
                        art.currentPrice,
                        art.bidStartTime,
                        art.closingTime,
                        art.imgPath,
                        art.artStatus,
                        art.closedAt,
                        winningBid.bidPrice
                    order by art.artId desc
                    """,
            countQuery = """
                    select count(art)
                    from Art art
                    join art.artist artist
                    where artist.artistCode = :artistCode
                      and art.artStatus in :artStatuses
                    """
    )
    Page<MyArtQueryDto> findMyArtSummaries(
            @Param("artistCode") String artistCode,
            @Param("artStatuses") List<Integer> artStatuses,
            Pageable pageable);
}
