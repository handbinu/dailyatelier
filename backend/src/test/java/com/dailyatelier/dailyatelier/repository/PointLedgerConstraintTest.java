package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.PaymentProvider;
import com.dailyatelier.dailyatelier.entity.PointCharge;
import com.dailyatelier.dailyatelier.entity.PointTransaction;
import com.dailyatelier.dailyatelier.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:point-ledger-constraints;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PointLedgerConstraintTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PointTransactionRepository pointTransactionRepository;

    @Autowired
    private PointChargeRepository pointChargeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final LocalDateTime now = LocalDateTime.of(2026, 7, 31, 12, 0);

    @BeforeEach
    void setUp() {
        userRepository.saveAndFlush(createUser("constraint-member"));
    }

    @Test
    void databaseRejectsNegativeAccountBalance() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into point_account (
                    user_id, available_balance, held_balance,
                    version, created_at, updated_at
                ) values (?, -1, 0, 0, ?, ?)
                """, "constraint-member", now, now))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void idempotencyKeyIsUnique() {
        pointTransactionRepository.saveAndFlush(
                PointTransaction.openingBalance(
                        "constraint-member",
                        100L,
                        now
                )
        );

        assertThatThrownBy(() -> pointTransactionRepository.saveAndFlush(
                PointTransaction.openingBalance(
                        "constraint-member",
                        100L,
                        now.plusSeconds(1)
                )
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void chargeBusinessKeysAreUnique() {
        pointChargeRepository.saveAndFlush(PointCharge.pending(
                "constraint-member",
                PaymentProvider.INTERNAL,
                "merchant-order",
                1_000L,
                "charge-key-1",
                now
        ));

        assertThatThrownBy(() -> pointChargeRepository.saveAndFlush(
                PointCharge.pending(
                        "constraint-member",
                        PaymentProvider.INTERNAL,
                        "merchant-order",
                        1_000L,
                        "charge-key-2",
                        now.plusSeconds(1)
                )
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private User createUser(String userId) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("password");
        user.setName("제약 사용자");
        user.setNickname("제약닉");
        user.setPhoneNumber("010-0000-0000");
        user.setEmail("constraint@example.com");
        user.setJoinDate(now);
        user.setUserStatus(0);
        return user;
    }
}
