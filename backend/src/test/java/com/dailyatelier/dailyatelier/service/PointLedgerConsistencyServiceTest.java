package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.config.TimeConfig;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.repository.PointAccountRepository;
import com.dailyatelier.dailyatelier.repository.PointConsistencyRepository;
import com.dailyatelier.dailyatelier.repository.PointTransactionRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:point-ledger-consistency;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({
        PointAccountService.class,
        PointLedgerConsistencyService.class,
        PointConsistencyRepository.class,
        TimeConfig.class
})
class PointLedgerConsistencyServiceTest {

    @Autowired
    private PointAccountService pointAccountService;

    @Autowired
    private PointLedgerConsistencyService consistencyService;

    @Autowired
    private PointAccountRepository pointAccountRepository;

    @Autowired
    private PointTransactionRepository pointTransactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void cleanUp() {
        pointTransactionRepository.deleteAll();
        pointAccountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void reportsConsistentAccountWhenLedgerSumsMatch() {
        saveLegacyUser("matched", 12_345);
        pointAccountService.initializeAccount("matched");

        assertThat(consistencyService.inspect().consistent()).isTrue();
        assertThat(consistencyService.inspect().mismatches()).isEmpty();
        assertThat(consistencyService.inspect().issues()).isEmpty();
    }

    @Test
    void reportsUsersAndLedgerOwnersWithoutPointAccounts() {
        saveLegacyUser("orphan", 0);
        insertTransaction(101, "orphan", "ADJUSTMENT_CREDIT", 1_000, 1_000, 0,
                "USER", "orphan", null);

        assertThat(issueTypes()).contains(
                PointLedgerConsistencyService.ConsistencyIssueType.USER_WITHOUT_ACCOUNT,
                PointLedgerConsistencyService.ConsistencyIssueType.LEDGER_WITHOUT_ACCOUNT);
    }

    @Test
    void reportsActiveHoldSumDifferentFromHeldBalance() {
        saveLegacyUser("hold-balance", 10_000);
        pointAccountService.initializeAccount("hold-balance");
        jdbcTemplate.update("""
                update point_account
                set available_balance = 9_000, held_balance = 1_000
                where user_id = 'hold-balance'
                """);
        insertTransaction(102, "hold-balance", "HOLD", 1_000, -1_000, 1_000,
                "BID", "202", null);

        assertThat(issueTypes()).contains(
                PointLedgerConsistencyService.ConsistencyIssueType.ACTIVE_HOLD_BALANCE_MISMATCH);
    }

    @Test
    void reportsHeldPointNotReferencedByItsArt() {
        saveLegacyUser("hold-owner", 1_000);
        pointAccountService.initializeAccount("hold-owner");
        jdbcTemplate.update("update point_account set available_balance = 0, held_balance = 1000 where user_id = 'hold-owner'");
        insertArt(301, null);
        insertBid(302, 301, "hold-owner", 1_000);
        insertHold(303, 301, "hold-owner", 302, 1_000, "HELD");
        insertTransaction(103, "hold-owner", "HOLD", 1_000, -1_000, 1_000,
                "BID", "302", null);

        assertThat(issueTypes()).contains(
                PointLedgerConsistencyService.ConsistencyIssueType.ACTIVE_HOLD_ART_REFERENCE_MISMATCH);
    }

    @Test
    void reportsArtReferencingPointHoldOwnedByAnotherArt() {
        saveLegacyUser("released-owner", 0);
        pointAccountService.initializeAccount("released-owner");
        insertArt(311, null);
        insertArt(312, null);
        insertBid(313, 312, "released-owner", 1_000);
        insertHold(314, 312, "released-owner", 313, 1_000, "RELEASED");
        jdbcTemplate.update("update art set active_point_hold_id = 314 where art_id = 311");

        assertThat(issueTypes()).contains(
                PointLedgerConsistencyService.ConsistencyIssueType.ART_ACTIVE_HOLD_MISMATCH);
    }

    @Test
    void reportsPaidOrderWithoutCommitTransaction() {
        insertOrderFixture(401, "PAID");

        assertThat(consistencyService.inspect().issues())
                .filteredOn(issue -> issue.type()
                        == PointLedgerConsistencyService.ConsistencyIssueType.ORDER_COMMIT_MISMATCH)
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.targetType()).isEqualTo("ORDER");
                    assertThat(issue.targetId()).isEqualTo("401");
                    assertThat(issue.reason()).isNotBlank();
                });
    }

    @Test
    void reportsRefundedOrderWithoutRefundTransaction() {
        insertOrderFixture(411, "REFUNDED");
        insertTransaction(412, "order-buyer", "COMMIT", 1_000, 0, -1_000,
                "ORDER", "411", null);

        assertThat(issueTypes()).contains(
                PointLedgerConsistencyService.ConsistencyIssueType.ORDER_REFUND_MISMATCH);
    }

    @Test
    void reportsPaidChargeWithoutChargeTransaction() {
        saveLegacyUser("charge-owner", 0);
        pointAccountService.initializeAccount("charge-owner");
        insertCharge(501, "charge-owner", "PAID", 1_000, null, null);

        assertThat(issueTypes()).contains(
                PointLedgerConsistencyService.ConsistencyIssueType.CHARGE_TRANSACTION_MISMATCH);
    }

    @Test
    void reportsRefundedChargeWithoutRefundTransaction() {
        saveLegacyUser("refund-owner", 0);
        pointAccountService.initializeAccount("refund-owner");
        insertTransaction(511, "refund-owner", "DEMO_CHARGE", 1_000, 1_000, 0,
                "CHARGE", "512", null);
        insertCharge(512, "refund-owner", "REFUNDED", 1_000, 511L, null);

        assertThat(issueTypes()).contains(
                PointLedgerConsistencyService.ConsistencyIssueType.CHARGE_REFUND_MISMATCH);
    }

    @Test
    void acceptsSemanticallyMatchedChargeAndRefundTransactions() {
        saveLegacyUser("normal-charge", 0);
        pointAccountService.initializeAccount("normal-charge");
        insertTransaction(521, "normal-charge", "DEMO_CHARGE", 1_000, 1_000, 0,
                "CHARGE", "523", null);
        insertTransaction(522, "normal-charge", "REFUND", 1_000, -1_000, 0,
                "CHARGE", "523", 521L);
        insertCharge(523, "normal-charge", "REFUNDED", 1_000, 521L, 522L);

        assertThat(consistencyService.inspect().consistent()).isTrue();
    }

    @Test
    void acceptsSemanticallyMatchedCommittedOrderFlow() {
        insertOrderFixture(531, "PAID");
        pointAccountService.initializeAccount("order-buyer");
        pointAccountService.initializeAccount("order-seller");
        insertTransaction(534, "order-buyer", "ADJUSTMENT_CREDIT", 1_000, 1_000, 0,
                "USER", "order-buyer", null);
        insertTransaction(535, "order-buyer", "HOLD", 1_000, -1_000, 1_000,
                "BID", "533", null);
        insertTransaction(536, "order-buyer", "COMMIT", 1_000, 0, -1_000,
                "ORDER", "531", null);
        insertHold(537, 532, "order-buyer", 533, 1_000, "COMMITTED");
        jdbcTemplate.update("update point_hold set commit_order_id = 531 where hold_id = 537");
        jdbcTemplate.update("update art set active_point_hold_id = 537 where art_id = 532");

        assertThat(consistencyService.inspect().consistent()).isTrue();
    }

    private Set<PointLedgerConsistencyService.ConsistencyIssueType> issueTypes() {
        return consistencyService.inspect().issues().stream()
                .map(PointLedgerConsistencyService.ConsistencyIssue::type)
                .collect(java.util.stream.Collectors.toSet());
    }

    private void insertTransaction(long transactionId, String userId, String type,
                                   long amount, long availableDelta, long heldDelta,
                                   String referenceType, String referenceId, Long reversalId) {
        jdbcTemplate.update("""
                insert into point_transaction (
                    transaction_id, user_id, type, amount, available_delta, held_delta,
                    available_balance_after, held_balance_after, reference_type, reference_id,
                    idempotency_key, reversal_of_transaction_id, created_at
                ) values (?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?, ?, current_timestamp)
                """, transactionId, userId, type, amount, availableDelta, heldDelta,
                referenceType, referenceId, "consistency:" + transactionId, reversalId);
    }

    private void insertArt(long artId, Long activeHoldId) {
        jdbcTemplate.update("""
                insert into art (
                    art_id, name, format, category, start_price, current_price,
                    bid_start_time, closing_time, img_path, art_status,
                    created_at, active_point_hold_id
                ) values (?, '테스트 작품', 'PHYSICAL', 'OTHER', 1000, 1000,
                          current_timestamp, current_timestamp, '/test.jpg', 0,
                          current_timestamp, ?)
                """, artId, activeHoldId);
    }

    private void insertBid(long bidId, long artId, String userId, int amount) {
        jdbcTemplate.update("""
                insert into bid (bid_id, user_id, art_id, bid_price, bid_time)
                values (?, ?, ?, ?, current_timestamp)
                """, bidId, userId, artId, amount);
    }

    private void insertHold(long holdId, long artId, String userId,
                            long bidId, long amount, String status) {
        jdbcTemplate.update("""
                insert into point_hold (
                    hold_id, art_id, user_id, latest_bid_id, amount, status,
                    created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                """, holdId, artId, userId, bidId, amount, status);
    }

    private void insertOrderFixture(long orderId, String status) {
        saveLegacyUser("order-buyer", 0);
        saveLegacyUser("order-seller", 0);
        insertArt(orderId + 1, null);
        insertBid(orderId + 2, orderId + 1, "order-buyer", 1_000);
        jdbcTemplate.update("""
                insert into orders (
                    order_id, art_id, winning_bid_id, buyer_id, seller_id,
                    buyer_id_snapshot, buyer_name_snapshot, buyer_nickname_snapshot,
                    buyer_phone_snapshot, seller_id_snapshot, seller_name_snapshot,
                    seller_nickname_snapshot, seller_artist_name_snapshot, seller_phone_snapshot,
                    art_id_snapshot, art_name_snapshot, art_image_snapshot,
                    winning_bid_id_snapshot, winning_price, payment_method, status,
                    created_at, payment_due_at
                ) values (?, ?, ?, 'order-buyer', 'order-seller',
                          'order-buyer', '구매자', '구매자', '010',
                          'order-seller', '판매자', '판매자', '작가', '010',
                          ?, '테스트 작품', '/test.jpg', ?, 1000, 'INTERNAL_POINT', ?,
                          current_timestamp, current_timestamp)
                """, orderId, orderId + 1, orderId + 2,
                orderId + 1, orderId + 2, status);
    }

    private void insertCharge(long chargeId, String userId, String status, long paidAmount,
                              Long chargeTransactionId, Long refundTransactionId) {
        jdbcTemplate.update("""
                insert into point_charge (
                    charge_id, user_id, provider, merchant_order_id, requested_amount,
                    paid_amount, status, idempotency_key, created_at,
                    charge_transaction_id, refund_transaction_id
                ) values (?, ?, 'INTERNAL', ?, ?, ?, ?, ?, current_timestamp, ?, ?)
                """, chargeId, userId, "merchant:" + chargeId, paidAmount, paidAmount,
                status, "charge-request:" + chargeId, chargeTransactionId, refundTransactionId);
    }

    @Test
    void reportsEveryAccountWhoseCurrentBalanceDiffersFromLedger() {
        saveLegacyUser("mismatched", 10_000);
        pointAccountService.initializeAccount("mismatched");
        jdbcTemplate.update("""
                update point_account
                set available_balance = 9_000, held_balance = 1_000
                where user_id = 'mismatched'
                """);
        entityManager.clear();

        PointLedgerConsistencyService.ConsistencyReport report =
                consistencyService.inspect();

        assertThat(report.consistent()).isFalse();
        assertThat(report.mismatches()).singleElement().satisfies(mismatch -> {
            assertThat(mismatch.userId()).isEqualTo("mismatched");
            assertThat(mismatch.accountAvailableBalance()).isEqualTo(9_000);
            assertThat(mismatch.accountHeldBalance()).isEqualTo(1_000);
            assertThat(mismatch.ledgerAvailableBalance()).isEqualTo(10_000);
            assertThat(mismatch.ledgerHeldBalance()).isZero();
        });
    }

    private void saveLegacyUser(String userId, int reserve) {
        userRepository.saveAndFlush(createUser(userId));
        jdbcTemplate.update(
                "update users set reserve = ? where user_id = ?",
                reserve,
                userId
        );
        entityManager.clear();
    }

    private User createUser(String userId) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("password");
        user.setName("테스트 사용자");
        user.setNickname("테스트닉");
        user.setPhoneNumber("010-0000-0000");
        user.setEmail(userId + "@example.com");
        user.setJoinDate(LocalDateTime.of(2026, 7, 31, 12, 0));
        user.setUserStatus(0);
        return user;
    }
}
