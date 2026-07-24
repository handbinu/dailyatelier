package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.config.TimeConfig;
import com.dailyatelier.dailyatelier.dto.BidCreateRequestDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.Bid;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.BidRepository;
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
    private BidRepository bidRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long artId;

    @BeforeEach
    void setUp() {
        bidRepository.deleteAll();
        artRepository.deleteAll();
        artistRepository.deleteAll();
        userRepository.deleteAll();

        User seller = createUser("seller", 1);
        saveUser("bidder", 0);

        Artist artist = new Artist();
        artist.setUser(seller);
        artist.setArtistName("테스트 작가");
        artist = artistRepository.save(artist);

        LocalDateTime now = LocalDateTime.now();
        Art art = new Art();
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

    private User saveUser(String userId, int userStatus) {
        return userRepository.save(createUser(userId, userStatus));
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
