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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:point-charge-concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({PointChargeService.class, PointAccountService.class,
        InternalPointPaymentProvider.class, TimeConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PointChargeServiceConcurrencyTest {
    @Autowired PointChargeService service;
    @Autowired PointAccountService pointAccountService;
    @Autowired PointChargeRepository chargeRepository;
    @Autowired PointAccountRepository accountRepository;
    @Autowired PointTransactionRepository transactionRepository;
    @Autowired UserRepository userRepository;
    Long chargeId;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAllInBatch();
        chargeRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        User user = new User();
        user.setUserId("concurrent");
        user.setPassword("password");
        user.setName("동시성 사용자");
        user.setNickname("동시성");
        user.setPhoneNumber("010-0000-0000");
        user.setEmail("concurrent@example.com");
        user.setJoinDate(LocalDateTime.now());
        user.setUserStatus(0);
        userRepository.saveAndFlush(user);
        pointAccountService.initializeAccount("concurrent");
        chargeId = service.create("concurrent", PaymentProvider.INTERNAL, 5_000, "create")
                .getChargeId();
    }

    @Test
    void concurrentApprovalCreditsBalanceAndLedgerOnlyOnce() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> approve(ready, start));
            Future<?> second = executor.submit(() -> approve(ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertThat(accountRepository.findById("concurrent").orElseThrow().getAvailableBalance())
                .isEqualTo(5_000);
        assertThat(transactionRepository.countByUserId("concurrent")).isEqualTo(1);
        assertThat(chargeRepository.findById(chargeId).orElseThrow().getStatus())
                .isEqualTo(PointChargeStatus.PAID);
    }

    private void approve(CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
            service.approve(chargeId,
                    new PaymentApproval(PaymentProvider.INTERNAL, null, 5_000, 5_000, true));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
