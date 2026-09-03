package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.config.TimeConfig;
import com.dailyatelier.dailyatelier.entity.PointAccount;
import com.dailyatelier.dailyatelier.entity.PointTransaction;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.dailyatelier.dailyatelier.jwt.JwtTokenProvider;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:point-account-transaction;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({PointAccountService.class, UserService.class, TimeConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PointAccountServiceTransactionTest {

    @MockitoBean
    private CloudinaryService cloudinaryService;

    @Autowired
    private PointAccountService pointAccountService;

    @Autowired
    private UserService userService;

    @Autowired
    private PointAccountRepository pointAccountRepository;

    @MockitoSpyBean
    private PointTransactionRepository pointTransactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void cleanUp() {
        pointTransactionRepository.deleteAll();
        pointAccountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void initializesNewUserWithZeroBalanceAndNoOpeningTransaction() {
        userRepository.saveAndFlush(createUser("new-member"));

        PointAccount account = pointAccountService.initializeAccount("new-member");

        assertThat(account.getAvailableBalance()).isZero();
        assertThat(account.getHeldBalance()).isZero();
        assertThat(pointTransactionRepository.countByUserId("new-member")).isZero();
    }

    @Test
    void registrationCreatesPointAccountInSameUseCase() {
        User user = createUser("registered-member");
        org.mockito.Mockito.when(passwordEncoder.encode("password"))
                .thenReturn("encoded-password");

        userService.registerUser(user);

        assertThat(userRepository.findById("registered-member")).isPresent();
        assertThat(pointAccountRepository.findById("registered-member"))
                .isPresent()
                .get()
                .extracting(PointAccount::getAvailableBalance)
                .isEqualTo(0L);
    }

    @Test
    void migratesLegacyBalanceOnlyOnceAndLedgerSumMatchesAccount() {
        saveUserWithLegacyReserve("legacy-member", 12_345);

        pointAccountService.initializeAccount("legacy-member");
        pointAccountService.initializeAccount("legacy-member");

        PointAccount account = pointAccountRepository.findById("legacy-member")
                .orElseThrow();
        assertThat(account.getAvailableBalance()).isEqualTo(12_345L);
        assertThat(account.getHeldBalance()).isZero();
        assertThat(pointTransactionRepository.countByUserId("legacy-member"))
                .isEqualTo(1L);
        assertThat(pointTransactionRepository
                .sumAvailableDeltaByUserId("legacy-member"))
                .isEqualTo(account.getAvailableBalance());
        assertThat(pointTransactionRepository
                .sumHeldDeltaByUserId("legacy-member"))
                .isEqualTo(account.getHeldBalance());
    }

    @Test
    void rollsBackAccountWhenOpeningTransactionSaveFails() {
        saveUserWithLegacyReserve("rollback-member", 1_000);
        doThrow(new IllegalStateException("원장 저장 실패"))
                .when(pointTransactionRepository)
                .save(any(PointTransaction.class));

        assertThatThrownBy(() ->
                pointAccountService.initializeAccount("rollback-member"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("원장 저장 실패");

        assertThat(pointAccountRepository.findById("rollback-member")).isEmpty();
        assertThat(pointTransactionRepository.countByUserId("rollback-member"))
                .isZero();
    }

    private void saveUserWithLegacyReserve(String userId, int reserve) {
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
