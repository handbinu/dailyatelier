package com.dailyatelier.dailyatelier.support;

import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.PointHoldStatus;
import com.dailyatelier.dailyatelier.dto.BidCreateRequestDto;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.OrderRepository;
import com.dailyatelier.dailyatelier.repository.PointHoldRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import com.dailyatelier.dailyatelier.service.BidService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "dailyatelier.local-demo.enabled=true",
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:local-demo-seed-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@ActiveProfiles("local-demo")
@Import(LocalDemoDataSeederTest.MutableClockConfiguration.class)
class LocalDemoDataSeederTest {
    private static final Instant INITIAL_INSTANT = Instant.parse("2026-09-22T00:00:00Z");
    @Autowired
    private LocalDemoDataSeeder seeder;
    @Autowired
    private ArtRepository artRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PointHoldRepository pointHoldRepository;
    @Autowired
    private MutableClock clock;
    @Autowired
    private BidService bidService;

    @BeforeEach
    void resetClock() {
        clock.set(INITIAL_INSTANT);
    }

    @Test
    @Transactional
    void createsStableArtworkAndSoldAuctionFixturesWithoutDuplicates() {
        long artCount = artRepository.count();
        long userCount = userRepository.count();
        long orderCount = orderRepository.count();

        seeder.seed();

        assertThat(artCount).isEqualTo(20);
        assertThat(userCount).isEqualTo(8);
        assertThat(artRepository.count()).isEqualTo(artCount);
        assertThat(userRepository.count()).isEqualTo(userCount);
        assertThat(orderRepository.count()).isEqualTo(orderCount).isEqualTo(4);
        assertThat(artRepository.findAll().stream()
                .filter(art -> art.getArtStatus() == Art.STATUS_ACTIVE)
                .count()).isEqualTo(14);
        assertThat(artRepository.findAll().stream()
                .filter(art -> art.getArtStatus() == Art.STATUS_UNSOLD)
                .count()).isEqualTo(2);
        assertThat(artRepository.findAll())
                .extracting(Art::getImgPath)
                .contains("/img/demo-art/art-demo-01-coral-blue.jpg",
                        "/img/demo-art/art-demo-20-sage.jpg");
        assertThat(artRepository.findAll().stream()
                .filter(art -> art.getArtStatus() == Art.STATUS_SOLD)
                .toList()).hasSize(4)
                .allSatisfy(art -> {
                    assertThat(art.getWinningBid()).isNotNull();
                    assertThat(art.getClosedAt()).isNotNull();
                    assertThat(art.getCurrentPrice()).isEqualTo(art.getWinningBid().getBidPrice());
                    assertThat(art.getActivePointHold().getStatus()).isEqualTo(PointHoldStatus.HELD);
                });
        assertThat(pointHoldRepository.findAll()).hasSize(4);
    }

    @Test
    @Transactional
    void restoresSafeOngoingAndUpcomingWindowsAfterSixDays() {
        long artCount = artRepository.count();
        long orderCount = orderRepository.count();
        long holdCount = pointHoldRepository.count();

        clock.set(INITIAL_INSTANT.plus(java.time.Duration.ofDays(6)));
        seeder.seed();

        LocalDateTime now = LocalDateTime.now(clock);
        assertThat(artRepository.count()).isEqualTo(artCount).isEqualTo(20);
        assertThat(orderRepository.count()).isEqualTo(orderCount).isEqualTo(4);
        assertThat(pointHoldRepository.count()).isEqualTo(holdCount).isEqualTo(4);
        assertThat(artRepository.findAll().stream()
                .filter(art -> art.getArtStatus() == Art.STATUS_ACTIVE)
                .filter(art -> !art.getBidStartTime().isAfter(now))
                .filter(art -> art.getClosingTime().isAfter(now))
                .count()).isEqualTo(8);
        assertThat(artRepository.findAll().stream()
                .filter(art -> art.getArtStatus() == Art.STATUS_ACTIVE)
                .filter(art -> art.getBidStartTime().isAfter(now))
                .filter(art -> art.getClosingTime().isAfter(now))
                .count()).isEqualTo(6);
        assertThat(artRepository.findAll().stream()
                .filter(art -> art.getArtStatus() == Art.STATUS_SOLD)
                .toList()).hasSize(4)
                .allSatisfy(art -> assertThat(art.getWinningBid()).isNotNull());
    }

    @Test
    @Transactional
    void doesNotRefreshQaArtworkWithAnActualBid() {
        Art art = artRepository.findAll().stream()
                .filter(candidate -> candidate.getName().equals("코랄 블루의 오후"))
                .findFirst()
                .orElseThrow();
        LocalDateTime originalClosingTime = art.getClosingTime();
        BidCreateRequestDto request = new BidCreateRequestDto();
        request.setBidPrice(art.getStartPrice() + art.getMinimumBidIncrement());
        bidService.createBid(art.getArtId(), "demo-buyer-one", request);

        clock.set(INITIAL_INSTANT.plus(java.time.Duration.ofDays(6)));
        seeder.seed();

        Art reloaded = artRepository.findById(art.getArtId()).orElseThrow();
        assertThat(reloaded.getClosingTime()).isEqualTo(originalClosingTime);
        assertThat(reloaded.getCurrentPrice()).isEqualTo(request.getBidPrice());
        assertThat(reloaded.getActivePointHold()).isNotNull();
    }

    @TestConfiguration
    static class MutableClockConfiguration {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(INITIAL_INSTANT, ZoneId.of("Asia/Seoul"));
        }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        MutableClock(Instant initialInstant, ZoneId zone) {
            this.instant = new AtomicReference<>(initialInstant);
            this.zone = zone;
        }

        void set(Instant nextInstant) {
            instant.set(nextInstant);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant.get(), zone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
