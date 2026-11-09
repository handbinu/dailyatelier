package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.dto.ArtistDetailResponseDto;
import com.dailyatelier.dailyatelier.dto.ArtistSummaryResponseDto;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ArtistRepository extends JpaRepository<Artist, String> {
    Optional<Artist> findByUser(User user);

    @Query(
            value = """
                    select new com.dailyatelier.dailyatelier.dto.ArtistSummaryResponseDto(
                        artist.artistCode,
                        artist.artistName,
                        artist.artistIntro,
                        count(activeArt)
                    )
                    from Artist artist
                    join artist.user user
                    left join Art activeArt
                      on activeArt.artist = artist
                     and activeArt.artStatus = :activeStatus
                     and activeArt.bidStartTime <= :now
                     and activeArt.closingTime > :now
                    where user.userStatus = :artistUserStatus
                      and (:keyword is null
                           or lower(artist.artistName) like lower(concat('%', :keyword, '%')))
                    group by artist.artistCode, artist.artistName, artist.artistIntro
                    order by artist.artistName asc, artist.artistCode asc
                    """,
            countQuery = """
                    select count(artist)
                    from Artist artist
                    join artist.user user
                    where user.userStatus = :artistUserStatus
                      and (:keyword is null
                           or lower(artist.artistName) like lower(concat('%', :keyword, '%')))
                    """
    )
    Page<ArtistSummaryResponseDto> findPublicArtists(
            @Param("keyword") String keyword,
            @Param("artistUserStatus") Integer artistUserStatus,
            @Param("activeStatus") Integer activeStatus,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Query("""
            select new com.dailyatelier.dailyatelier.dto.ArtistDetailResponseDto(
                artist.artistCode,
                artist.artistName,
                artist.artistIntro,
                count(activeArt)
            )
            from Artist artist
            join artist.user user
            left join Art activeArt
              on activeArt.artist = artist
             and activeArt.artStatus = :activeStatus
             and activeArt.bidStartTime <= :now
             and activeArt.closingTime > :now
            where artist.artistCode = :artistId
              and user.userStatus = :artistUserStatus
            group by artist.artistCode, artist.artistName, artist.artistIntro
            """)
    Optional<ArtistDetailResponseDto> findPublicArtistDetail(
            @Param("artistId") String artistId,
            @Param("artistUserStatus") Integer artistUserStatus,
            @Param("activeStatus") Integer activeStatus,
            @Param("now") LocalDateTime now);
}

