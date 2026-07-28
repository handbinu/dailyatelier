package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.dto.MyArtQueryDto;
import com.dailyatelier.dailyatelier.dto.BidSummaryQueryDto;
import com.dailyatelier.dailyatelier.dto.MyArtState;
import com.dailyatelier.dailyatelier.dto.WinningArtResponseDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.Bid;
import com.dailyatelier.dailyatelier.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auction-result-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuctionResultRepositoryTest {
    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 7, 27, 18, 0);

    @Autowired
    private ArtRepository artRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BidRepository bidRepository;

    private Artist artist;
    private User winner;
    private User other;
    private Art activeArt;
    private Art unsoldArt;
    private Art canceledArt;
    private Art olderWin;
    private Art newerWin;

    @BeforeEach
    void setUp() {
        User seller = createUser("seller", 1);
        artist = new Artist();
        artist.setUser(seller);
        artist.setArtistName("테스트 작가");
        artist = artistRepository.save(artist);
        winner = userRepository.save(createUser("winner", 0));
        other = userRepository.save(createUser("other", 0));

        activeArt = saveArt("진행 작품", Art.STATUS_ACTIVE, null);
        saveBid(activeArt, winner, 110_000, BASE_TIME.minusHours(2));

        unsoldArt = saveArt("유찰 작품", Art.STATUS_UNSOLD, BASE_TIME.plusSeconds(1));
        canceledArt = saveArt(
                "작가 취소 작품",
                Art.STATUS_CANCELED,
                BASE_TIME.plusSeconds(2)
        );
        saveBid(canceledArt, winner, 120_000, BASE_TIME.minusHours(1));

        olderWin = saveSoldArt(
                "먼저 낙찰",
                winner,
                130_000,
                BASE_TIME.plusSeconds(3)
        );
        newerWin = saveSoldArt(
                "최근 낙찰",
                winner,
                150_000,
                BASE_TIME.plusSeconds(4)
        );
        saveSoldArt(
                "다른 사용자 낙찰",
                other,
                170_000,
                BASE_TIME.plusSeconds(5)
        );
    }

    @Test
    void winningArtsAreIsolatedSortedAndPagedByUser() {
        Page<WinningArtResponseDto> firstPage = artRepository.findWinningArtsByUserId(
                winner.getUserId(),
                Art.STATUS_SOLD,
                PageRequest.of(0, 1)
        );
        Page<WinningArtResponseDto> secondPage = artRepository.findWinningArtsByUserId(
                winner.getUserId(),
                Art.STATUS_SOLD,
                PageRequest.of(1, 1)
        );

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getContent())
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.getArtId()).isEqualTo(newerWin.getArtId());
                    assertThat(result.getWinningPrice()).isEqualTo(150_000);
                    assertThat(result.getClosedAt()).isEqualTo(BASE_TIME.plusSeconds(4));
                });
        assertThat(secondPage.getContent())
                .extracting(WinningArtResponseDto::getArtId)
                .containsExactly(olderWin.getArtId());
    }

    @Test
    void myArtSummaryFiltersStatesAndAggregatesBidData() {
        Page<MyArtQueryDto> active = artRepository.findMyArtSummaries(
                artist.getArtistCode(),
                List.of(Art.STATUS_ACTIVE),
                PageRequest.of(0, 12)
        );
        Page<MyArtQueryDto> ended = artRepository.findMyArtSummaries(
                artist.getArtistCode(),
                MyArtState.ENDED.getArtStatuses(),
                PageRequest.of(0, 12)
        );

        assertThat(active.getContent())
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.getArtId()).isEqualTo(activeArt.getArtId());
                    assertThat(result.getBidCount()).isEqualTo(1L);
                    assertThat(result.getWinningPrice()).isNull();
                });
        assertThat(ended.getTotalElements()).isEqualTo(5);
        assertThat(ended.getContent())
                .filteredOn(result -> result.getArtId().equals(unsoldArt.getArtId()))
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.getBidCount()).isZero();
                    assertThat(result.getWinningPrice()).isNull();
                });
        assertThat(ended.getContent())
                .filteredOn(result -> result.getArtId().equals(canceledArt.getArtId()))
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.getArtStatus()).isEqualTo(Art.STATUS_CANCELED);
                    assertThat(result.getBidCount()).isEqualTo(1L);
                    assertThat(result.getWinningPrice()).isNull();
                });
        assertThat(ended.getContent())
                .filteredOn(result -> result.getArtId().equals(newerWin.getArtId()))
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.getBidCount()).isEqualTo(1L);
                    assertThat(result.getWinningPrice()).isEqualTo(150_000);
                });
    }

    @Test
    void bidSummaryContainsWinnerOnlyForUsersOwnBidArts() {
        Page<BidSummaryQueryDto> summaries = bidRepository.findBidSummariesByUserId(
                winner.getUserId(),
                PageRequest.of(0, 12)
        );

        assertThat(summaries.getTotalElements()).isEqualTo(4);
        assertThat(summaries.getContent())
                .filteredOn(result -> result.getArtId().equals(newerWin.getArtId()))
                .singleElement()
                .satisfies(result ->
                        assertThat(result.getWinningUserId()).isEqualTo(winner.getUserId()));
        assertThat(summaries.getContent())
                .filteredOn(result -> result.getArtId().equals(activeArt.getArtId()))
                .singleElement()
                .satisfies(result -> assertThat(result.getWinningUserId()).isNull());
        assertThat(summaries.getContent())
                .filteredOn(result -> result.getArtId().equals(canceledArt.getArtId()))
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.getArtStatus()).isEqualTo(Art.STATUS_CANCELED);
                    assertThat(result.getWinningUserId()).isNull();
                });
    }

    private Art saveSoldArt(
            String name,
            User winningUser,
            int winningPrice,
            LocalDateTime closedAt) {
        Art art = saveArt(name, Art.STATUS_ACTIVE, null);
        Bid winningBid = saveBid(
                art,
                winningUser,
                winningPrice,
                closedAt.minusMinutes(1)
        );
        art.setCurrentPrice(winningPrice);
        art.setWinningBid(winningBid);
        art.setClosedAt(closedAt);
        art.setArtStatus(Art.STATUS_SOLD);
        return artRepository.save(art);
    }

    private Art saveArt(String name, int artStatus, LocalDateTime closedAt) {
        Art art = new Art();
        art.setArtist(artist);
        art.setName(name);
        art.setStartPrice(100_000);
        art.setCurrentPrice(100_000);
        art.setBidStartTime(BASE_TIME.minusDays(1));
        art.setClosingTime(BASE_TIME);
        art.setImgPath("https://example.com/art.jpg");
        art.setArtStatus(artStatus);
        art.setClosedAt(closedAt);
        return artRepository.save(art);
    }

    private Bid saveBid(
            Art art,
            User user,
            int bidPrice,
            LocalDateTime bidTime) {
        Bid bid = new Bid();
        bid.setArt(art);
        bid.setUser(user);
        bid.setBidPrice(bidPrice);
        bid.setBidTime(bidTime);
        return bidRepository.save(bid);
    }

    private User createUser(String userId, int userStatus) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("encoded-password");
        user.setName("테스트 사용자");
        user.setNickname(userId);
        user.setPhoneNumber("010-0000-0000");
        user.setEmail(userId + "@example.com");
        user.setJoinDate(BASE_TIME.minusDays(10));
        user.setUserStatus(userStatus);
        return user;
    }
}
