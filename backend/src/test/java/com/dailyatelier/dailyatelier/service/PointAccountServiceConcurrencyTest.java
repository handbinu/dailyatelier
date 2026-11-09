package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.config.TimeConfig;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.repository.PointAccountRepository;
import com.dailyatelier.dailyatelier.repository.PointTransactionRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:point-account-concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({PointAccountService.class, TimeConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PointAccountServiceConcurrencyTest {

    @Autowired
    private PointAccountService pointAccountService;

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
    void setUp() {
        pointTransactionRepository.deleteAll();
        pointAccountRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.saveAndFlush(createUser("concurrent-member"));
        jdbcTemplate.update(
                "update users set reserve = 5000 where user_id = ?",
                "concurrent-member"
        );
        entityManager.clear();
    }

    @Test
    void concurrentInitializationCreatesOneAccountAndOneOpeningTransaction()
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> initialize(ready, start));
            Future<?> second = executor.submit(() -> initialize(ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(pointAccountRepository.count()).isEqualTo(1L);
        assertThat(pointTransactionRepository
                .countByUserId("concurrent-member"))
                .isEqualTo(1L);
        assertThat(pointAccountRepository.findById("concurrent-member")
                .orElseThrow()
                .getAvailableBalance()).isEqualTo(5_000L);
    }

    private void initialize(CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 실행 시작 대기 시간 초과");
            }
            pointAccountService.initializeAccount("concurrent-member");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private User createUser(String userId) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("password");
        user.setName("동시성 사용자");
        user.setNickname("동시성닉");
        user.setPhoneNumber("010-0000-0000");
        user.setEmail("concurrency@example.com");
        user.setJoinDate(LocalDateTime.of(2026, 7, 31, 12, 0));
        user.setUserStatus(0);
        return user;
    }
}
