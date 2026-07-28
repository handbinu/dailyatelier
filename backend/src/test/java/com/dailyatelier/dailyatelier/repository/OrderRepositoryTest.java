package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.Address;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.Bid;
import com.dailyatelier.dailyatelier.entity.Order;
import com.dailyatelier.dailyatelier.entity.OrderShippingAddress;
import com.dailyatelier.dailyatelier.entity.OrderStatus;
import com.dailyatelier.dailyatelier.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class OrderRepositoryTest {
    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2026, 7, 28, 12, 0);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private ArtRepository artRepository;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private EntityManager entityManager;

    private User buyer;
    private User seller;
    private Artist artist;
    private Art art;
    private Bid winningBid;
    private Address buyerAddress;

    @BeforeEach
    void setUp() {
        buyer = userRepository.save(createUser("buyer", "구매자", "구매닉", 0));
        seller = userRepository.save(createUser("seller", "판매자", "판매닉", 1));

        artist = new Artist();
        artist.setUser(seller);
        artist.setArtistName("원래 작가명");
        artist = artistRepository.save(artist);

        art = new Art();
        art.setArtist(artist);
        art.setName("원래 작품명");
        art.setStartPrice(100_000);
        art.setCurrentPrice(100_000);
        art.setBidStartTime(CREATED_AT.minusDays(1));
        art.setClosingTime(CREATED_AT);
        art.setImgPath("https://example.com/original.jpg");
        art.setArtStatus(Art.STATUS_ACTIVE);
        art = artRepository.save(art);

        winningBid = new Bid();
        winningBid.setArt(art);
        winningBid.setUser(buyer);
        winningBid.setBidPrice(150_000);
        winningBid.setBidTime(CREATED_AT.minusMinutes(1));
        winningBid = bidRepository.save(winningBid);

        art.setCurrentPrice(winningBid.getBidPrice());
        art.setWinningBid(winningBid);
        art.setClosedAt(CREATED_AT);
        art.setArtStatus(Art.STATUS_SOLD);
        art = artRepository.save(art);

        buyerAddress = new Address();
        buyerAddress.setUser(buyer);
        buyerAddress.setZipCode("12345");
        buyerAddress.setUserAddress1("원래 주소");
        buyerAddress.setUserAddress2("101호");
        buyerAddress = addressRepository.save(buyerAddress);
    }

    @Test
    void enforcesOneOrderPerArtWithDatabaseUniqueConstraint() {
        orderRepository.saveAndFlush(createOrder(null));

        assertThatThrownBy(() ->
                orderRepository.saveAndFlush(createOrder(null))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsOrderByArtAndWithWriteLock() {
        Order saved = orderRepository.saveAndFlush(createOrder(null));
        entityManager.clear();

        Order byArt = orderRepository.findByArtArtId(art.getArtId()).orElseThrow();
        Order locked = orderRepository.findByIdForUpdate(
                saved.getOrderId()
        ).orElseThrow();

        assertThat(byArt.getOrderId()).isEqualTo(saved.getOrderId());
        assertThat(locked.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(orderRepository.existsByArtArtId(art.getArtId())).isTrue();
    }

    @Test
    void persistsSnapshotsIndependentlyFromSourceChanges() {
        Order saved = orderRepository.saveAndFlush(createOrder(
                OrderShippingAddress.of(
                        buyer.getName(),
                        buyer.getPhoneNumber(),
                        "01234",
                        buyerAddress.getUserAddress1(),
                        buyerAddress.getUserAddress2()
                )
        ));

        buyer.setName("변경된 구매자");
        buyer.setNickname("변경구매닉");
        seller.setName("변경된 판매자");
        seller.setNickname("변경판매닉");
        artist.setArtistName("변경된 작가명");
        art.setName("변경된 작품명");
        art.setImgPath("https://example.com/changed.jpg");
        winningBid.setBidPrice(999_999);
        buyerAddress.setZipCode("99999");
        buyerAddress.setUserAddress1("변경된 주소");
        buyerAddress.setUserAddress2("999호");

        entityManager.flush();
        entityManager.clear();

        Order reloaded = orderRepository.findById(saved.getOrderId()).orElseThrow();

        assertThat(reloaded.getBuyerNameSnapshot()).isEqualTo("구매자");
        assertThat(reloaded.getBuyerNicknameSnapshot()).isEqualTo("구매닉");
        assertThat(reloaded.getSellerNameSnapshot()).isEqualTo("판매자");
        assertThat(reloaded.getSellerNicknameSnapshot()).isEqualTo("판매닉");
        assertThat(reloaded.getSellerArtistNameSnapshot()).isEqualTo("원래 작가명");
        assertThat(reloaded.getArtNameSnapshot()).isEqualTo("원래 작품명");
        assertThat(reloaded.getArtImageSnapshot())
                .isEqualTo("https://example.com/original.jpg");
        assertThat(reloaded.getWinningPrice()).isEqualTo(150_000);
        assertThat(reloaded.getShippingAddress().getZipCode()).isEqualTo("01234");
        assertThat(reloaded.getShippingAddress().getAddress1()).isEqualTo("원래 주소");
        assertThat(reloaded.getShippingAddress().getAddress2()).isEqualTo("101호");
    }

    private Order createOrder(OrderShippingAddress address) {
        return Order.create(
                art,
                winningBid,
                buyer,
                seller,
                CREATED_AT,
                CREATED_AT.plusHours(24),
                address
        );
    }

    private User createUser(
            String userId,
            String name,
            String nickname,
            int userStatus) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("encoded-password");
        user.setName(name);
        user.setNickname(nickname);
        user.setPhoneNumber("010-0000-0000");
        user.setEmail(userId + "@example.com");
        user.setJoinDate(CREATED_AT.minusDays(10));
        user.setUserStatus(userStatus);
        return user;
    }
}
