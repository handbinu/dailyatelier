package com.dailyatelier.dailyatelier;

import com.dailyatelier.dailyatelier.dto.BidCreateRequestDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.ArtCategory;
import com.dailyatelier.dailyatelier.entity.ArtFormat;
import com.dailyatelier.dailyatelier.entity.Artist;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.OrderRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import com.dailyatelier.dailyatelier.service.AuctionCloseResult;
import com.dailyatelier.dailyatelier.service.AuctionCloseScheduler;
import com.dailyatelier.dailyatelier.service.AuctionCloseService;
import com.dailyatelier.dailyatelier.service.BidService;
import com.dailyatelier.dailyatelier.service.OrderExpirationScheduler;
import com.dailyatelier.dailyatelier.service.OrderExpirationService;
import com.dailyatelier.dailyatelier.service.PointAccountService;
import com.dailyatelier.dailyatelier.service.PointLedgerConsistencyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:due-batch-progress;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.flyway.enabled=${AUDIT_FLYWAY_ENABLED:false}", "spring.jpa.hibernate.ddl-auto=${AUDIT_DDL_MODE:create-drop}",
    "auction.close.interval-ms=3600000", "order.payment-expiration.interval-ms=3600000"
})
class DueBatchProgressIntegrationTest {
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired ArtistRepository artists;
    @Autowired TransactionTemplate transaction;
    @Autowired ArtRepository arts;
    @Autowired OrderRepository orderRows;
    @Autowired PointAccountService accounts;
    @Autowired BidService bids;
    @Autowired AuctionCloseService close;
    @Autowired OrderExpirationService expiration;
    @Autowired PointLedgerConsistencyService consistency;

    @Test
    void advancesPastFailedBatchAndRevisitsItAfterSweep() {
        Fixture fixture = fixture("sweep");
        long blockedOrder = pending(fixture, "blocked-order");
        long healthyOrder = pending(fixture, "healthy-order");
        long newOrder = pending(fixture, "new-order");
        long blockedArt = bidOn(fixture, "blocked-art");
        long healthyArt = bidOn(fixture, "healthy-art");
        long newArt = bidOn(fixture, "new-art");

        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        LocalDateTime firstCutoff = now.minusMinutes(2);
        LocalDateTime initialDeadline = now.minusMinutes(3);
        LocalDateTime newDeadline = now.minusMinutes(1);
        jdbc.update("update orders set payment_due_at=? where order_id in (?, ?)",
                initialDeadline, blockedOrder, healthyOrder);
        jdbc.update("update orders set payment_due_at=? where order_id=?", newDeadline, newOrder);
        jdbc.update("update art set closing_time=? where art_id in (?, ?)",
                initialDeadline, blockedArt, healthyArt);
        jdbc.update("update art set closing_time=? where art_id=?", newDeadline, newArt);

        long badHold = jdbc.queryForObject("""
                select a.active_point_hold_id from art a join orders o on a.art_id=o.art_id
                where o.order_id=?
                """, Long.class, blockedOrder);
        jdbc.update("update point_hold set amount=10999 where hold_id=?", badHold);
        jdbc.update("update art set current_price=10999 where art_id=?", blockedArt);

        var currentTime = new AtomicReference<>(
                firstCutoff.atZone(clock.getZone()).toInstant());
        Clock schedulerClock = mock(Clock.class);
        when(schedulerClock.getZone()).thenReturn(clock.getZone());
        when(schedulerClock.instant()).thenAnswer(invocation -> currentTime.get());
        var closeJob = new AuctionCloseScheduler(close, arts, schedulerClock, 1);
        var expiryJob = new OrderExpirationScheduler(expiration, orderRows, schedulerClock, 1);

        var openingAccount = account(fixture);
        closeJob.closeExpiredAuctionsOnStartup();
        expiryJob.expireOrdersOnStartup();
        assertThat(account(fixture)).isEqualTo(openingAccount);
        assertThat(orderCount(blockedArt)).isZero();
        assertThat(releaseCount(blockedOrder)).isZero();
        assertThat(orderCount(healthyArt)).isZero();
        assertThat(status(healthyOrder)).isEqualTo("PAYMENT_PENDING");

        currentTime.set(now.atZone(clock.getZone()).toInstant());
        new AuctionCloseScheduler(close, arts, schedulerClock, 1).closeExpiredAuctionsOnStartup();
        new OrderExpirationScheduler(expiration, orderRows, schedulerClock, 1).expireOrdersOnStartup();
        assertThat(orderCount(healthyArt)).isZero();
        assertThat(status(healthyOrder)).isEqualTo("PAYMENT_PENDING");

        closeJob.closeExpiredAuctions();
        expiryJob.expireOrders();
        assertThat(orderCount(healthyArt)).isEqualTo(1);
        assertThat(status(healthyOrder)).isEqualTo("CANCELED");
        assertThat(releaseCount(healthyOrder)).isEqualTo(1);
        assertThat(orderCount(blockedArt)).isZero();
        assertThat(status(blockedOrder)).isEqualTo("PAYMENT_PENDING");

        closeJob.closeExpiredAuctions();
        expiryJob.expireOrders();
        assertThat(orderCount(newArt)).isZero();
        assertThat(status(newOrder)).isEqualTo("PAYMENT_PENDING");

        jdbc.update("update point_hold set amount=11000 where hold_id=?", badHold);
        jdbc.update("update art set current_price=11000 where art_id=?", blockedArt);
        closeJob.closeExpiredAuctions();
        expiryJob.expireOrders();
        assertThat(orderCount(blockedArt)).isEqualTo(1);
        assertThat(status(blockedOrder)).isEqualTo("CANCELED");
        assertThat(releaseCount(blockedOrder)).isEqualTo(1);

        closeJob.closeExpiredAuctions();
        expiryJob.expireOrders();
        assertThat(orderCount(newArt)).isEqualTo(1);
        assertThat(status(newOrder)).isEqualTo("CANCELED");
        assertThat(releaseCount(newOrder)).isEqualTo(1);
        assertThat(releaseCount(healthyOrder)).isEqualTo(1);
        assertThat(consistency.inspect().consistent()).isTrue();
    }

    private Map<String, Object> account(Fixture fixture) {
        return jdbc.queryForMap("select * from point_account where user_id=?", fixture.buyer());
    }

    private int orderCount(long artId) {
        return jdbc.queryForObject("select count(*) from orders where art_id=?", Integer.class, artId);
    }

    private String status(long id) {
        return jdbc.queryForObject("select status from orders where order_id=?", String.class, id);
    }

    private int releaseCount(long id) {
        return jdbc.queryForObject("""
                select count(*) from point_transaction
                where reference_type='ORDER' and reference_id=? and type='RELEASE'
                """, Integer.class, Long.toString(id));
    }


    private Fixture fixture(String prefix) {
        User buyer = addUser(prefix + "buyer", 0);
        User seller = addUser(prefix + "sell", 1);
        jdbc.update("update users set reserve=? where user_id=?", 1000000, buyer.getUserId());
        accounts.initializeAccount(buyer.getUserId());
        accounts.initializeAccount(seller.getUserId());
        Artist artist = transaction.execute(status -> {
            Artist created = new Artist();
            created.setUser(users.findById(seller.getUserId()).orElseThrow());
            created.setArtistName("Audit fixture");
            return artists.saveAndFlush(created);
        });
        return new Fixture(buyer.getUserId(), artist);
    }

    private User addUser(String name, int role) {
        User u = new User();
        u.setUserId(name);
        u.setName(name);
        u.setNickname(name);
        u.setPassword("fixture-hash");
        u.setPhoneNumber("010-0000-0000");
        u.setEmail(name + "@example.test");
        u.setJoinDate(LocalDateTime.now(clock));
        u.setUserStatus(role);
        return users.saveAndFlush(u);
    }

    private long art(Fixture f, String name) {
        LocalDateTime now = LocalDateTime.now(clock);
        Art a = new Art();
        a.setArtist(f.artist());
        a.setName(name);
        a.setFormat(ArtFormat.PHYSICAL);
        a.setCategory(ArtCategory.OTHER);
        a.setStartPrice(10000);
        a.setCurrentPrice(10000);
        a.setMinimumBidIncrement(1000);
        a.setBidStartTime(now.minusHours(4));
        a.setClosingTime(now.plusHours(4));
        a.setImgPath("https://example.test/fixture.jpg");
        a.setArtStatus(Art.STATUS_ACTIVE);
        return arts.saveAndFlush(a).getArtId();
    }

    private long bidOn(Fixture f, String name) {
        long artId = art(f, name);
        BidCreateRequestDto bid = new BidCreateRequestDto();
        bid.setBidPrice(11000);
        bids.createBid(artId, f.buyer(), bid);
        return artId;
    }

    private long pending(Fixture f, String name) {
        long artId = bidOn(f, name);
        jdbc.update("update art set closing_time=? where art_id=?", LocalDateTime.now(clock).minusMinutes(1), artId);
        assertThat(close.closeAuction(artId)).isEqualTo(AuctionCloseResult.SOLD);
        return jdbc.queryForObject("select order_id from orders where art_id=?", Long.class, artId);
    }

    private record Fixture(String buyer, Artist artist) {}
}





