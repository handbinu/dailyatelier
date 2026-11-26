package com.dailyatelier.dailyatelier;

import com.dailyatelier.dailyatelier.dto.BidCreateRequestDto;
import com.dailyatelier.dailyatelier.dto.OrderShippingAddressRequestDto;
import com.dailyatelier.dailyatelier.entity.*;
import com.dailyatelier.dailyatelier.repository.*;
import com.dailyatelier.dailyatelier.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:order-idempotency;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.flyway.enabled=${AUDIT_FLYWAY_ENABLED:false}", "spring.jpa.hibernate.ddl-auto=${AUDIT_DDL_MODE:create-drop}",
    "auction.close.interval-ms=3600000", "order.payment-expiration.interval-ms=3600000"
})
@AutoConfigureMockMvc
class OrderIdempotencyIntegrationTest {
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ArtistRepository artists;
    @Autowired org.springframework.transaction.support.TransactionTemplate transaction;
    @Autowired ArtRepository arts;

    @Autowired PointAccountService accounts;
    @Autowired BidService bids;
    @Autowired AuctionCloseService close;
    @Autowired OrderService orders;
    @Autowired OrderStateService states;
    @Autowired PointLedgerConsistencyService consistency;

    @Test
    void validatesHeadersAndScopesKeysAcrossOrdersAndOperations() throws Exception {
        Fixture f = fixture("wire");
        long first = pending(f, "first");
        long second = pending(f, "second");
        String shared = "x".repeat(150);

        Snapshot initial = snapshot(first, f.buyer());
        assertInvalid(payment(first), f.buyer(), "USER", null);
        assertInvalid(payment(first), f.buyer(), "USER", "   ");
        assertInvalid(payment(first), f.buyer(), "USER", "x".repeat(151));
        assertThat(snapshot(first, f.buyer())).isEqualTo(initial);

        assertOk(payment(first), f.buyer(), "USER", " " + shared + " ");
        Snapshot paid = snapshot(first, f.buyer());
        assertOk(payment(first), f.buyer(), "USER", shared);
        assertInvalid(payment(first), f.buyer(), "USER", "x".repeat(151));
        assertThat(snapshot(first, f.buyer())).isEqualTo(paid);
        assertOk(payment(second), f.buyer(), "USER", shared);
        assertThat(ledgerKey(first, "COMMIT")).isEqualTo("order:v2:commit:" + first);
        assertThat(ledgerKey(second, "COMMIT")).isEqualTo("order:v2:commit:" + second);

        states.requestRefund(first, f.buyer(), "fixture refund");
        Snapshot requested = snapshot(first, f.buyer());
        String approve = approval(first);
        assertInvalid(approve, f.seller(), "ARTIST", null);
        assertInvalid(approve, f.seller(), "ARTIST", "   ");
        assertInvalid(approve, f.seller(), "ARTIST", "x".repeat(151));
        assertThat(snapshot(first, f.buyer())).isEqualTo(requested);

        assertOk(approve, f.seller(), "ARTIST", shared);
        Snapshot refunded = snapshot(first, f.buyer());
        assertOk(approve, f.seller(), "ARTIST", "order-refund:" + first);
        assertInvalid(approve, f.seller(), "ARTIST", "x".repeat(151));
        assertThat(snapshot(first, f.buyer())).isEqualTo(refunded);
        assertThat(ledgerKey(first, "REFUND")).isEqualTo("order:v2:refund:" + first);
        assertThat(consistency.inspect().consistent()).isTrue();
    }

    @Test
    void handlesConcurrentRetriesAndLegacyKeyOccupation() throws Exception {
        Fixture f = fixture("retry");
        long legacy = pending(f, "legacy");
        long target = pending(f, "target");
        assertOk(payment(legacy), f.buyer(), "USER", "order-payment:" + legacy);
        String occupiedCommitKey = "order:v2:commit:" + target;
        jdbc.update("update point_transaction set idempotency_key=? where idempotency_key=?",
                occupiedCommitKey, "order:v2:commit:" + legacy);

        runConcurrently(() -> assertOk(payment(target), f.buyer(), "USER", "order-payment:" + target));
        assertThat(ledgerKey(legacy, "COMMIT")).isEqualTo(occupiedCommitKey);
        assertThat(ledgerKey(target, "COMMIT")).startsWith(occupiedCommitKey + ":");

        states.requestRefund(legacy, f.buyer(), "legacy refund");
        assertOk(approval(legacy), f.seller(), "ARTIST", "order-refund:" + legacy);
        String occupiedRefundKey = "order:v2:refund:" + target;
        jdbc.update("update point_transaction set idempotency_key=? where idempotency_key=?",
                occupiedRefundKey, "order:v2:refund:" + legacy);
        states.requestRefund(target, f.buyer(), "target refund");
        runConcurrently(() -> assertOk(approval(target), f.seller(), "ARTIST", "order-refund:" + target));

        assertThat(ledgerKey(legacy, "REFUND")).isEqualTo(occupiedRefundKey);
        assertThat(ledgerKey(target, "REFUND")).startsWith(occupiedRefundKey + ":");
        for (long orderId : new long[] {legacy, target}) {
            Long commit = jdbc.queryForObject("""
                    select transaction_id from point_transaction
                    where reference_type='ORDER' and reference_id=? and type='COMMIT'
                    """, Long.class, Long.toString(orderId));
            assertThat(jdbc.queryForObject("""
                    select reversal_of_transaction_id from point_transaction
                    where reference_type='ORDER' and reference_id=? and type='REFUND'
                    """, Long.class, Long.toString(orderId))).isEqualTo(commit);
        }
        assertThat(consistency.inspect().consistent()).isTrue();
    }

    @Test
    void rollsBackRefundApprovalWhenTheDatabaseRejectsLedgerInsert() throws Exception {
        Fixture f = fixture("rb");
        long orderId = pending(f, "rollback");
        assertOk(payment(orderId), f.buyer(), "USER", "order-payment:" + orderId);
        states.requestRefund(orderId, f.buyer(), "fixture refund");
        Snapshot requested = snapshot(orderId, f.buyer());

        jdbc.execute("""
                alter table point_transaction add constraint ck_audit_refund_failure
                check (not (reference_type='ORDER' and type='REFUND' and reference_id='%d'))
                """.formatted(orderId));
        mvc.perform(post(approval(orderId)).with(principal(f.seller(), "ARTIST"))
                        .header("Idempotency-Key", "order-refund:" + orderId))
                .andExpect(status().isInternalServerError());
        assertThat(snapshot(orderId, f.buyer())).isEqualTo(requested);
        assertThat(consistency.inspect().consistent()).isTrue();
    }

    private void runConcurrently(Callable<Void> request) throws Exception {
        var start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> task = () -> {
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Request start timed out");
                }
                return request.call();
            };
            var first = workers.submit(task);
            var second = workers.submit(task);
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
        }
    }

    private Void assertOk(String path, String id, String role, String key) throws Exception {
        mvc.perform(post(path).with(principal(id, role)).header("Idempotency-Key", key))
                .andExpect(status().isOk());
        return null;
    }

    private void assertInvalid(String path, String id, String role, String key) throws Exception {
        var request = post(path).with(principal(id, role));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        mvc.perform(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_REQUEST"))
                .andExpect(jsonPath("$.path").value(path));
    }

    private String approval(long orderId) {
        return "/api/artists/me/orders/" + orderId + "/refund/approve";
    }

    private String ledgerKey(long orderId, String type) {
        return jdbc.queryForObject("""
                select idempotency_key from point_transaction
                where reference_type='ORDER' and reference_id=? and type=?
                """, String.class, Long.toString(orderId), type);
    }


    private org.springframework.test.web.servlet.request.RequestPostProcessor principal(String id, String role) {
        return authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                id, null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role))));
    }

    private Snapshot snapshot(long id, String buyer) {
        return new Snapshot(
                jdbc.queryForMap("select * from orders where order_id=?", id),
                jdbc.queryForMap("select * from point_hold where hold_id=(select a.active_point_hold_id from art a join orders o on o.art_id=a.art_id where o.order_id=?)", id),
                jdbc.queryForMap("select * from point_account where user_id=?", buyer),
                jdbc.queryForList("select * from point_transaction where user_id=? order by transaction_id", buyer));
    }

    private String payment(long id) { return "/api/users/me/orders/" + id + "/payment"; }

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
        return new Fixture(buyer.getUserId(), seller.getUserId(), artist);
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

    private long pending(Fixture f, String name) {
        long artId = art(f, name);
        BidCreateRequestDto bid = new BidCreateRequestDto();
        bid.setBidPrice(11000);
        bids.createBid(artId, f.buyer(), bid);
        jdbc.update("update art set closing_time=? where art_id=?", LocalDateTime.now(clock).minusMinutes(1), artId);
        assertThat(close.closeAuction(artId)).isEqualTo(AuctionCloseResult.SOLD);
        long id = jdbc.queryForObject("select order_id from orders where art_id=?", Long.class, artId);
        OrderShippingAddressRequestDto address = new OrderShippingAddressRequestDto();
        address.setRecipientName("Audit Fixture");
        address.setRecipientPhone("010-0000-0000");
        address.setZipCode("12345");
        address.setAddress1("Audit fixture address");
        orders.confirmShippingAddress(id, f.buyer(), address);
        return id;
    }

    record Fixture(String buyer, String seller, Artist artist) {}
    record Snapshot(Map<String,Object> order, Map<String,Object> hold,
                    Map<String,Object> account, List<Map<String,Object>> ledger) {}
}





