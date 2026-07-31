package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.config.TimeConfig;
import com.dailyatelier.dailyatelier.entity.*;
import com.dailyatelier.dailyatelier.payment.InternalPointPaymentProvider;
import com.dailyatelier.dailyatelier.payment.PaymentApproval;
import com.dailyatelier.dailyatelier.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:point-charge-tx;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({PointChargeService.class, PointAccountService.class,
        InternalPointPaymentProvider.class, TimeConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PointChargeServiceTransactionTest {
    @Autowired PointChargeService service;
    @Autowired PointAccountService pointAccountService;
    @Autowired PointChargeRepository chargeRepository;
    @Autowired PointAccountRepository accountRepository;
    @MockitoSpyBean PointTransactionRepository transactionRepository;
    @Autowired UserRepository userRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAllInBatch();
        chargeRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        userRepository.saveAndFlush(user("member"));
        pointAccountService.initializeAccount("member");
    }

    @Test
    void createIsIdempotentButRejectsDifferentRequest() {
        PointCharge first = service.create("member", PaymentProvider.INTERNAL, 1_000, "same");
        PointCharge replay = service.create("member", PaymentProvider.INTERNAL, 1_000, "same");
        assertThat(replay.getChargeId()).isEqualTo(first.getChargeId());
        assertThatThrownBy(() ->
                service.create("member", PaymentProvider.INTERNAL, 2_000, "same"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approvalAndRefundWriteOppositeLedgerEntriesExactlyOnce() {
        PointCharge charge = service.create("member", PaymentProvider.INTERNAL, 1_000, "create");
        PaymentApproval approval = approval(1_000);
        service.approve(charge.getChargeId(), approval);
        service.approve(charge.getChargeId(), approval);
        assertThat(accountRepository.findById("member").orElseThrow().getAvailableBalance())
                .isEqualTo(1_000);
        assertThat(transactionRepository.countByUserId("member")).isEqualTo(1);

        service.refund(charge.getChargeId());
        service.refund(charge.getChargeId());
        PointCharge refunded = chargeRepository.findById(charge.getChargeId()).orElseThrow();
        assertThat(refunded.getStatus()).isEqualTo(PointChargeStatus.REFUNDED);
        assertThat(accountRepository.findById("member").orElseThrow().getAvailableBalance())
                .isZero();
        assertThat(transactionRepository.countByUserId("member")).isEqualTo(2);
        assertThat(transactionRepository.sumAvailableDeltaByUserId("member")).isZero();
    }

    @Test
    void rejectsAmountMismatchUnauthorizedApprovalAndPgOrderDuplication() {
        PointCharge first = service.create("member", PaymentProvider.INTERNAL, 1_000, "first");
        assertThatThrownBy(() -> service.approve(first.getChargeId(), approval(999)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.approve(first.getChargeId(),
                new PaymentApproval(PaymentProvider.INTERNAL, null, 1_000, 1_000, false)))
                .isInstanceOf(SecurityException.class);

        userRepository.saveAndFlush(user("other"));
        pointAccountService.initializeAccount("other");
        PointCharge external1 = chargeRepository.saveAndFlush(PointCharge.pending(
                "member", PaymentProvider.NAVER_PAY, "m1", 1_000, "n1", LocalDateTime.now()));
        PointCharge external2 = chargeRepository.saveAndFlush(PointCharge.pending(
                "other", PaymentProvider.NAVER_PAY, "m2", 1_000, "n2", LocalDateTime.now()));
        external1.approve("pg-duplicate", 1_000, 10L, LocalDateTime.now());
        chargeRepository.saveAndFlush(external1);
        external2.approve("pg-duplicate", 1_000, 11L, LocalDateTime.now());
        assertThatThrownBy(() -> chargeRepository.saveAndFlush(external2))
                .isInstanceOf(Exception.class);
    }

    @Test
    void ledgerFailureRollsBackBalanceAndChargeState() {
        PointCharge charge = service.create("member", PaymentProvider.INTERNAL, 1_000, "rollback");
        doThrow(new IllegalStateException("원장 저장 실패"))
                .when(transactionRepository).saveAndFlush(any(PointTransaction.class));

        assertThatThrownBy(() -> service.approve(charge.getChargeId(), approval(1_000)))
                .hasMessage("원장 저장 실패");
        assertThat(accountRepository.findById("member").orElseThrow().getAvailableBalance())
                .isZero();
        assertThat(chargeRepository.findById(charge.getChargeId()).orElseThrow().getStatus())
                .isEqualTo(PointChargeStatus.PENDING);
    }

    private PaymentApproval approval(long amount) {
        return new PaymentApproval(PaymentProvider.INTERNAL, null, amount, amount, true);
    }

    private User user(String id) {
        User user = new User();
        user.setUserId(id);
        user.setPassword("password");
        user.setName("테스트 사용자");
        user.setNickname(id);
        user.setPhoneNumber("010-0000-0000");
        user.setEmail(id + "@example.com");
        user.setJoinDate(LocalDateTime.of(2026, 7, 31, 12, 0));
        user.setUserStatus(0);
        return user;
    }
}
