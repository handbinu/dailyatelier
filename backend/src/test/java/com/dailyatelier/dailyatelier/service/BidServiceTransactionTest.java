package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.config.TimeConfig;
import com.dailyatelier.dailyatelier.dto.BidCreateRequestDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.Bid;
import com.dailyatelier.dailyatelier.entity.PointHoldStatus;
import com.dailyatelier.dailyatelier.entity.PointTransaction;
import com.dailyatelier.dailyatelier.entity.PointTransactionType;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.exception.BidApiException;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.BidRepository;
import com.dailyatelier.dailyatelier.repository.PointAccountRepository;
import com.dailyatelier.dailyatelier.repository.PointHoldRepository;
import com.dailyatelier.dailyatelier.repository.PointTransactionRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:bid-transaction-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({BidService.class, TimeConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BidServiceTransactionTest {

    @Autowired
    private BidService bidService;

    @Autowired
    private ArtRepository artRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoSpyBean
    private PointAccountRepository pointAccountRepository;

    @MockitoSpyBean
    private PointHoldRepository pointHoldRepository;

    @MockitoSpyBean
    private PointTransactionRepository pointTransactionRepository;

    @MockitoSpyBean
    private BidRepository bidRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long artId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("UPDATE art SET active_point_hold_id = NULL");
        pointTransactionRepository.deleteAll();
        pointHoldRepository.deleteAll();
        pointAccountRepository.deleteAll();
        bidRepository.deleteAll();
        artRepository.deleteAll();
        artistRepository.deleteAll();
        userRepository.deleteAll();

        User seller = createUser("seller", 1);
        saveUser("bidder", 0);
        insertPointAccount("bidder", 1_000_000L);

        Artist artist = new Artist();
        artist.setUser(seller);
        artist.setArtistName("테스트 작가");
        artist = artistRepository.save(artist);

        LocalDateTime now = LocalDateTime.now();
        Art art = new Art();
        art.setFormat(com.dailyatelier.dailyatelier.entity.ArtFormat.PHYSICAL);
        art.setCategory(com.dailyatelier.dailyatelier.entity.ArtCategory.OTHER);
        art.setArtist(artist);
        art.setName("트랜잭션 테스트 작품");
        art.setStartPrice(100_000);
        art.setCurrentPrice(100_000);
        art.setBidStartTime(now.minusDays(1));
        art.setClosingTime(now.plusDays(1));
        art.setImgPath("https://example.com/art.jpg");
        art.setArtStatus(0);
        artId = artRepository.save(art).getArtId();
    }

    @AfterEach
    void removeCurrentPriceConstraint() {
        jdbcTemplate.execute(
                "ALTER TABLE art DROP CONSTRAINT IF EXISTS chk_art_current_price_test"
        );
    }

    @Test
    void bidAndCurrentPriceAreCommittedTogether() {
        BidCreateRequestDto request = createRequest(120_000);

        bidService.createBid(artId, "bidder", request);

        assertThat(artRepository.findById(artId).orElseThrow().getCurrentPrice())
                .isEqualTo(120_000);
        assertThat(bidRepository.findAll())
                .singleElement()
                .satisfies(bid -> {
                    assertThat(bid.getArt().getArtId()).isEqualTo(artId);
                    assertThat(bid.getUser().getUserId()).isEqualTo("bidder");
                    assertThat(bid.getBidPrice()).isEqualTo(120_000);
                });
    }

    @Test
    void bidSaveFailureRollsBackCurrentPriceUpdate() {
        doThrow(new DataIntegrityViolationException("forced bid save failure"))
                .when(bidRepository).save(any(Bid.class));

        assertThatThrownBy(() ->
                bidService.createBid(artId, "bidder", createRequest(120_000)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(artRepository.findById(artId).orElseThrow().getCurrentPrice())
                .isEqualTo(100_000);
        assertThat(bidRepository.count()).isZero();
    }

    @Test
    void currentPriceSaveFailureRollsBackInsertedBid() {
        jdbcTemplate.execute("""
                ALTER TABLE art
                ADD CONSTRAINT chk_art_current_price_test
                CHECK (current_price <= 110000)
                """);

        assertThatThrownBy(() ->
                bidService.createBid(artId, "bidder", createRequest(120_000)))
                .isInstanceOf(DataIntegrityViolationException.class);

        var bidCaptor = org.mockito.ArgumentCaptor.forClass(Bid.class);
        verify(bidRepository).save(bidCaptor.capture());
        assertThat(bidCaptor.getValue().getBidId()).isNotNull();
        assertThat(artRepository.findById(artId).orElseThrow().getCurrentPrice())
                .isEqualTo(100_000);
        assertThat(bidRepository.count()).isZero();
    }

    @Test
    void holdsFullAmountIncreasesOnlyDifferenceAndReleasesOutbidHold() {
        saveUser("other", 0);
        insertPointAccount("other", 500_000L);

        bidService.createBid(artId, "bidder", createRequest(120_000));
        bidService.createBid(artId, "bidder", createRequest(150_000));
        bidService.createBid(artId, "other", createRequest(180_000));
        bidService.createBid(artId, "bidder", createRequest(200_000));

        assertThat(pointAccountRepository.findById("bidder").orElseThrow())
                .satisfies(account -> {
                    assertThat(account.getAvailableBalance()).isEqualTo(800_000L);
                    assertThat(account.getHeldBalance()).isEqualTo(200_000L);
                });
        assertThat(pointAccountRepository.findById("other").orElseThrow())
                .satisfies(account -> {
                    assertThat(account.getAvailableBalance()).isEqualTo(500_000L);
                    assertThat(account.getHeldBalance()).isZero();
                });
        assertThat(pointHoldRepository.findAll())
                .extracting(hold -> hold.getStatus())
                .containsExactly(
                        PointHoldStatus.RELEASED,
                        PointHoldStatus.RELEASED,
                        PointHoldStatus.HELD
                );
        assertThat(pointTransactionRepository.findAll())
                .extracting(PointTransaction::getType)
                .containsExactly(
                        PointTransactionType.HOLD,
                        PointTransactionType.HOLD_INCREASE,
                        PointTransactionType.HOLD,
                        PointTransactionType.RELEASE,
                        PointTransactionType.HOLD,
                        PointTransactionType.RELEASE
                );
        assertThat(artRepository.findById(artId).orElseThrow().getCurrentPrice())
                .isEqualTo(200_000);
    }

    @Test
    void allowsExactBalanceAndRejectsOnePointShortWithoutPartialChanges() {
        jdbcTemplate.update(
                "UPDATE point_account SET available_balance = 120000 WHERE user_id = 'bidder'"
        );
        bidService.createBid(artId, "bidder", createRequest(120_000));
        assertThat(pointAccountRepository.findById("bidder").orElseThrow())
                .satisfies(account -> {
                    assertThat(account.getAvailableBalance()).isZero();
                    assertThat(account.getHeldBalance()).isEqualTo(120_000L);
                });

        saveUser("short", 0);
        insertPointAccount("short", 129_999L);
        assertThatThrownBy(() ->
                bidService.createBid(artId, "short", createRequest(130_000)))
                .isInstanceOf(BidApiException.class)
                .satisfies(error -> assertThat(((BidApiException) error).getCode())
                        .isEqualTo("INSUFFICIENT_POINTS"));

        assertThat(artRepository.findById(artId).orElseThrow().getCurrentPrice())
                .isEqualTo(120_000);
        assertThat(bidRepository.count()).isEqualTo(1);
        assertThat(pointHoldRepository.count()).isEqualTo(1);
        assertThat(pointTransactionRepository.count()).isEqualTo(1);
    }

    @Test
    void ledgerSaveFailureRollsBackBidHoldAccountAndCurrentPrice() {
        doThrow(new DataIntegrityViolationException("forced ledger failure"))
                .when(pointTransactionRepository).save(any(PointTransaction.class));

        assertThatThrownBy(() ->
                bidService.createBid(artId, "bidder", createRequest(120_000)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(artRepository.findById(artId).orElseThrow().getCurrentPrice())
                .isEqualTo(100_000);
        assertThat(pointAccountRepository.findById("bidder").orElseThrow())
                .satisfies(account -> {
                    assertThat(account.getAvailableBalance()).isEqualTo(1_000_000L);
                    assertThat(account.getHeldBalance()).isZero();
                });
        assertThat(bidRepository.count()).isZero();
        assertThat(pointHoldRepository.count()).isZero();
        assertThat(pointTransactionRepository.count()).isZero();
    }

    @Test
    void accountSaveFailureRollsBackEntireBidTransaction() {
        doThrow(new DataIntegrityViolationException("forced account failure"))
                .when(pointAccountRepository).save(any());

        assertThatThrownBy(() ->
                bidService.createBid(artId, "bidder", createRequest(120_000)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertBidPointStateFullyRolledBack();
    }

    @Test
    void holdSaveFailureRollsBackEntireBidTransaction() {
        doThrow(new DataIntegrityViolationException("forced hold failure"))
                .when(pointHoldRepository).save(any());

        assertThatThrownBy(() ->
                bidService.createBid(artId, "bidder", createRequest(120_000)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertBidPointStateFullyRolledBack();
    }

    private void assertBidPointStateFullyRolledBack() {
        assertThat(artRepository.findById(artId).orElseThrow().getCurrentPrice())
                .isEqualTo(100_000);
        assertThat(pointAccountRepository.findById("bidder").orElseThrow())
                .satisfies(account -> {
                    assertThat(account.getAvailableBalance()).isEqualTo(1_000_000L);
                    assertThat(account.getHeldBalance()).isZero();
                });
        assertThat(bidRepository.count()).isZero();
        assertThat(pointHoldRepository.count()).isZero();
        assertThat(pointTransactionRepository.count()).isZero();
    }

    private User saveUser(String userId, int userStatus) {
        return userRepository.save(createUser(userId, userStatus));
    }

    private void insertPointAccount(String userId, long balance) {
        jdbcTemplate.update("""
                INSERT INTO point_account (
                    user_id, available_balance, held_balance, version, created_at, updated_at
                ) VALUES (?, ?, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId, balance);
    }

    private User createUser(String userId, int userStatus) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("encoded-password");
        user.setName("테스트 사용자");
        user.setNickname(userId);
        user.setPhoneNumber("010-0000-0000");
        user.setEmail(userId + "@example.com");
        user.setJoinDate(LocalDateTime.now());
        user.setUserStatus(userStatus);
        return user;
    }

    private BidCreateRequestDto createRequest(int bidPrice) {
        BidCreateRequestDto request = new BidCreateRequestDto();
        request.setBidPrice(bidPrice);
        return request;
    }
}
