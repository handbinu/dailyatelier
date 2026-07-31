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

import java.time.LocalDateTime;

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
