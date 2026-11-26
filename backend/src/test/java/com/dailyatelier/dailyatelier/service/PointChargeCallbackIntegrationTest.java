package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.PaymentCallbackStatus;
import com.dailyatelier.dailyatelier.entity.PaymentProvider;
import com.dailyatelier.dailyatelier.entity.PointCharge;
import com.dailyatelier.dailyatelier.entity.PointChargeStatus;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.payment.PaymentApproval;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:point-charge-callback-integration;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=${AUDIT_FLYWAY_ENABLED:false}",
        "spring.jpa.open-in-view=true",
        "spring.jpa.hibernate.ddl-auto=${AUDIT_DDL_MODE:create-drop}",
        "auction.close.interval-ms=3600000", "order.payment-expiration.interval-ms=3600000"
})
@AutoConfigureMockMvc
class PointChargeCallbackIntegrationTest {
    private static final PaymentProvider PROVIDER = PaymentProvider.INTERNAL;
    private static final PaymentApproval APPROVAL = new PaymentApproval(PROVIDER, null, 10000, 10000, true);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired EntityManager entityManager;
    @Autowired UserRepository users;
    @Autowired PointAccountService accounts;
    @MockitoSpyBean PointChargeService charges;
    @Autowired PaymentCallbackService callbacks;
    @Autowired PointLedgerConsistencyService consistency;
    @MockitoSpyBean PaymentCallbackTransactionService callbackTransactions;

    @Test
    void resumesCommittedPendingChargeAndDeduplicatesAfterDiscardedResponse() throws Exception {
        String user = fixture("phase2resume", "p2resume");
        long original = charges.create(user, PROVIDER, 10000, "resume-key").getChargeId();
        assertThat(chargeStatus(original)).isEqualTo("PENDING");
        assertThat(creditCount(user)).isZero();
        assertThat(accounts.getAvailableBalance(user)).isZero();

        long firstHttpId = pay(user, "resume-key");
        long replayId = pay(user, "resume-key");
        assertThat(firstHttpId).isEqualTo(original);
        assertThat(replayId).isEqualTo(original);
        assertPaidOnce(user, original);
        request(user, "resume-key", 30000).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertPaidOnce(user, original);
    }

    @Test
    void concurrentFirstRequestsWithSameKeyReturnSameChargeAndCreditOnce() throws Exception {
        String user = fixture("chargeshared", "cshared");
        var results = approveTogether(user, "same-key", "same-key");
        assertThat(results.get(0)).isEqualTo(results.get(1));
        assertThat(pay(user, "same-key")).isEqualTo(results.get(0));
        assertPaidOnce(user, results.get(0));
    }

    @Test
    void concurrentFirstRequestsWithDifferentKeysUseTheLatestAccountBalance() throws Exception {
        String user = fixture("chargedistinct", "cdistinct");
        var results = approveTogether(user, "first-key", "second-key");
        assertThat(results.get(0)).isNotEqualTo(results.get(1));
        assertThat(chargeCount(user)).isEqualTo(2);
        assertThat(creditCount(user)).isEqualTo(2);
        assertThat(accounts.getAvailableBalance(user)).isEqualTo(20000);
        assertThat(consistency.inspect().consistent()).isTrue();
    }

    private List<Long> approveTogether(String user, String firstKey, String secondKey) throws Exception {
        var approvalCallsReady = new CountDownLatch(2);
        var releaseApprovals = new CountDownLatch(1);
        doAnswer(invocation -> {
            approvalCallsReady.countDown();
            assertThat(releaseApprovals.await(10, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(charges).approve(anyLong(), eq(APPROVAL));

        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> pay(user, firstKey));
            var second = workers.submit(() -> pay(user, secondKey));
            assertThat(approvalCallsReady.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(chargeCount(user)).isEqualTo(firstKey.equals(secondKey) ? 1 : 2);
            assertThat(creditCount(user)).isZero();
            releaseApprovals.countDown();
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally {
            releaseApprovals.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void refreshKeepsChargeLockedAndPreservesOtherPendingChanges() throws Exception {
        String user = fixture("chargelocked", "clocked");
        long chargeId = charges.create(user, PROVIDER, 10000, "locked-key").getChargeId();
        var approved = new CountDownLatch(1);
        var allowCommit = new CountDownLatch(1);
        var lockAttempt = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var approval = workers.submit(() -> transactions.executeWithoutResult(status -> {
                var charge = entityManager.find(PointCharge.class, chargeId);
                assertThat(charge.getStatus()).isEqualTo(PointChargeStatus.PENDING);
                users.findById(user).orElseThrow().setName("Pending user update");
                charges.approve(chargeId, APPROVAL);
                assertThat(entityManager.getLockMode(charge)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
                approved.countDown();
                await(allowCommit);
            }));
            assertThat(approved.await(10, TimeUnit.SECONDS)).isTrue();
            var contender = workers.submit(() -> transactions.execute(status -> {
                lockAttempt.countDown();
                return jdbc.queryForObject("select status from point_charge where charge_id=? for update",
                        String.class, chargeId);
            }));
            assertThat(lockAttempt.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> contender.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            allowCommit.countDown();
            approval.get(10, TimeUnit.SECONDS);
            assertThat(contender.get(10, TimeUnit.SECONDS)).isEqualTo("PAID");
            assertThat(users.findById(user).orElseThrow().getName()).isEqualTo("Pending user update");
            assertPaidOnce(user, chargeId);
        } finally {
            allowCommit.countDown();
            workers.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void validatesChargeHeaderBeforeWritingAndKeepsValidKeysVerbatim() throws Exception {
        String user = fixture("chargeinvalid", "cinvalid");
        for (String key : new String[] {null, "   ", "x".repeat(151)}) {
            request(user, key, 10000).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_POINT_REQUEST"));
        }
        assertThat(chargeCount(user)).isZero();
        assertThat(creditCount(user)).isZero();
        assertThat(accounts.getAvailableBalance(user)).isZero();

        String boundaryKey = " " + "x".repeat(148) + " ";
        long boundary = pay(user, boundaryKey);
        assertThat(jdbc.queryForObject("select idempotency_key from point_charge where charge_id=?",
                String.class, boundary)).isEqualTo(boundaryKey);
        assertThat(pay(user, boundaryKey)).isEqualTo(boundary);
        assertThat(pay(user, java.util.UUID.randomUUID().toString())).isNotEqualTo(boundary);
        assertThat(creditCount(user)).isEqualTo(2);
        assertThat(accounts.getAvailableBalance(user)).isEqualTo(20000);
    }


    @Test
    void observesLateFailureAfterAnotherProcessorCommits() throws Exception {
        String user = fixture("phase2callback", "p2callback");
        long charge = charges.create(user, PROVIDER, 10000, "callback-charge").getChargeId();
        String eventId = "phase2-callback-race";
        long eventRow = callbacks.receive(PROVIDER, eventId, "fixture-order", "fixture-hash")
                .getCallbackEventId();
        assertThat(callbacks.receive(PROVIDER, eventId, "fixture-order", "fixture-hash")
                .getCallbackEventId()).isEqualTo(eventRow);

        var recordingFailure = new CountDownLatch(1);
        var allowLateFailure = new CountDownLatch(1);
        doAnswer(invocation -> {
            recordingFailure.countDown();
            assertThat(allowLateFailure.await(10, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(callbackTransactions).recordFailure(eq(PROVIDER), eq(eventId), anyString());

        var workers = Executors.newSingleThreadExecutor();
        try {
            var first = workers.submit(() -> callbacks.process(PROVIDER, eventId, event -> {
                charges.approve(charge, APPROVAL);
                throw new IllegalStateException("fixture failure after attempted credit");
            }));
            assertThat(recordingFailure.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(chargeStatus(charge)).isEqualTo("PENDING");
            assertThat(creditCount(user)).isZero();
            assertThat(accounts.getAvailableBalance(user)).isZero();

            var successful = callbacks.process(PROVIDER, eventId, event -> charges.approve(charge, APPROVAL));
            assertThat(successful.getStatus()).isEqualTo(PaymentCallbackStatus.PROCESSED);
            LocalDateTime processedAt = jdbc.queryForObject("""
                    select processed_at from payment_callback_event where callback_event_id=?
                    """, LocalDateTime.class, eventRow);
            allowLateFailure.countDown();

            var lateResult = first.get(10, TimeUnit.SECONDS);
            assertThat(lateResult.getStatus()).isEqualTo(PaymentCallbackStatus.PROCESSED);
            assertThat(lateResult.getAttemptCount()).isEqualTo(successful.getAttemptCount());
            assertThat(lateResult.getProcessedAt()).isEqualTo(processedAt);
            assertThat(lateResult.getLastError()).isNull();
            AtomicInteger calls = new AtomicInteger();
            var replay = callbacks.process(PROVIDER, eventId, event -> calls.incrementAndGet());
            assertThat(calls).hasValue(0);
            assertThat(replay.getStatus()).isEqualTo(PaymentCallbackStatus.PROCESSED);
            assertThat(replay.getAttemptCount()).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    select count(*) from payment_callback_event
                    where provider=? and provider_event_id=?
                    """, Long.class, PROVIDER.name(), eventId)).isEqualTo(1);
            assertPaidOnce(user, charge);
        } finally {
            allowLateFailure.countDown();
            workers.shutdownNow();
        }
    }

    private long pay(String user, String key) throws Exception {
        String body = request(user, key, 10000).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("chargeId").asLong();
    }

    private ResultActions request(String user, String key, long amount) throws Exception {
        var principal = new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        var request = post("/api/users/me/points/charges").with(authentication(principal))
                .contentType("application/json").content("{\"amount\":" + amount + "}");
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request);
    }

    private void assertPaidOnce(String user, long charge) {
        assertThat(chargeStatus(charge)).isEqualTo("PAID");
        assertThat(chargeCount(user)).isEqualTo(1);
        assertThat(creditCount(user)).isEqualTo(1);
        assertThat(accounts.getAvailableBalance(user)).isEqualTo(10000);
        assertThat(consistency.inspect().consistent()).isTrue();
    }

    private String chargeStatus(long charge) {
        return jdbc.queryForObject("select status from point_charge where charge_id=?", String.class, charge);
    }

    private long chargeCount(String user) {
        return jdbc.queryForObject("select count(*) from point_charge where user_id=?", Long.class, user);
    }

    private long creditCount(String user) {
        return jdbc.queryForObject("""
                select count(*) from point_transaction
                where user_id=? and reference_type='CHARGE' and type='DEMO_CHARGE'
                """, Long.class, user);
    }

    private String fixture(String id, String nickname) {
        User user = new User();
        user.setUserId(id);
        user.setPassword("fixture-hash");
        user.setName("Audit fixture");
        user.setNickname(nickname);
        user.setPhoneNumber("010-0000-0000");
        user.setEmail(id + "@example.test");
        user.setJoinDate(LocalDateTime.now());
        user.setUserStatus(0);
        users.saveAndFlush(user);
        accounts.initializeAccount(id);
        return id;
    }
}




