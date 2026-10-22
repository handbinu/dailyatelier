package com.dailyatelier.dailyatelier.jwt;

import com.dailyatelier.dailyatelier.dto.LoginRequestDto;
import com.dailyatelier.dailyatelier.dto.LoginResponseDto;
import com.dailyatelier.dailyatelier.dto.ProfileUpdateDto;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.exception.DomainApiException;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import com.dailyatelier.dailyatelier.service.PointAccountService;
import com.dailyatelier.dailyatelier.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate"
        }
)
@EnabledIfEnvironmentVariable(
        named = "DAILYATELIER_JWT_MYSQL_TEST",
        matches = "true"
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JwtInvalidationMySqlIntegrationTest {

    private static final String PRIMARY_USER_ID = "mysql-jwt-primary";
    private static final String SECONDARY_USER_ID = "mysql-jwt-secondary";
    private static final String CURRENT_PASSWORD = "current-password";
    private static final String PRIMARY_NICKNAME = "jwtprimary";
    private static final String SECONDARY_NICKNAME = "jwtsecond";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PointAccountService pointAccountService;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private boolean fixtureInitialized;

    @BeforeEach
    void setUp() throws Exception {
        verifyDedicatedJwtDatabase();
        deleteFixtures();
        saveUser(PRIMARY_USER_ID, PRIMARY_NICKNAME, 2);
        saveUser(SECONDARY_USER_ID, SECONDARY_NICKNAME, 0);
        pointAccountService.initializeAccount(PRIMARY_USER_ID);
        fixtureInitialized = true;
    }

    @AfterEach
    void tearDown() {
        if (fixtureInitialized) {
            deleteFixtures();
        }
    }

    @Test
    void passwordChangeInvalidatesOldJwtAndNewLoginWorks() {
        String oldToken = login(CURRENT_PASSWORD);

        assertThat(updateProfile(oldToken, CURRENT_PASSWORD, "changed-password", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(authVersion()).isEqualTo(1);
        assertThat(protectedProfile(oldToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(protectedProfile(login("changed-password")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void failedPasswordChangeLeavesVersionAndOldJwtUsable() {
        String oldToken = login(CURRENT_PASSWORD);
        String originalHash = passwordHash();

        assertThat(updateProfile(oldToken, "wrong-password", "changed-password", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(passwordHash()).isEqualTo(originalHash);
        assertThat(authVersion()).isZero();
        assertThat(protectedProfile(oldToken).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void duplicateNicknameFailureRollsBackPasswordAndAuthVersionAfterMutation() {
        String oldToken = login(CURRENT_PASSWORD);
        String originalHash = passwordHash();

        assertThat(updateProfile(
                oldToken,
                CURRENT_PASSWORD,
                "changed-password",
                SECONDARY_NICKNAME
        ).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(passwordHash()).isEqualTo(originalHash);
        assertThat(authVersion()).isZero();
        assertThat(protectedProfile(oldToken).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void concurrentPasswordChangesSerializeUnderUserLockThenOldJwtIsRejected() throws Exception {
        String oldToken = login(CURRENT_PASSWORD);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> changePasswordConcurrently(
                            ready, start, "concurrent-password-a")),
                    executor.submit(() -> changePasswordConcurrently(
                            ready, start, "concurrent-password-b"))
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int successCount = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }
            assertThat(successCount).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertThat(authVersion()).isEqualTo(1);
        assertThat(protectedProfile(oldToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void roleChangeInvalidatesAdminJwtAndNewTokenUsesCurrentDatabaseRole() {
        String oldAdminToken = login(CURRENT_PASSWORD);

        assertThat(adminInquiries(oldAdminToken).getStatusCode()).isEqualTo(HttpStatus.OK);
        userService.changeUserStatus(PRIMARY_USER_ID, 0);

        assertThat(authVersion()).isEqualTo(1);
        assertThat(adminInquiries(oldAdminToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(adminInquiries(login(CURRENT_PASSWORD)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private boolean changePasswordConcurrently(
            CountDownLatch ready,
            CountDownLatch start,
            String newPassword) {
        try {
            ProfileUpdateDto request = new ProfileUpdateDto();
            request.setCurrentPw(CURRENT_PASSWORD);
            request.setNewPw(newPassword);
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 비밀번호 변경 시작 대기 시간이 초과되었습니다");
            }
            userService.updateUserProfile(PRIMARY_USER_ID, request);
            return true;
        } catch (DomainApiException exception) {
            assertThat(exception.getCode()).isEqualTo("CURRENT_PASSWORD_MISMATCH");
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void verifyDedicatedJwtDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL())
                    .matches("jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):.*");
            assertThat(connection.getCatalog()).isEqualTo("dailyatelier_jwt_integration_test");
        }
    }

    private void saveUser(String userId, String nickname, int userStatus) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword(passwordEncoder.encode(CURRENT_PASSWORD));
        user.setName("MySQL JWT 검증 사용자");
        user.setNickname(nickname);
        user.setPhoneNumber("010-0000-0000");
        user.setEmail(userId + "@test.dev");
        user.setJoinDate(LocalDateTime.of(2026, 10, 21, 12, 0));
        user.setUserStatus(userStatus);
        userRepository.saveAndFlush(user);
    }

    private String login(String password) {
        LoginRequestDto request = new LoginRequestDto();
        request.setUserId(PRIMARY_USER_ID);
        request.setPassword(password);
        ResponseEntity<LoginResponseDto> response = restTemplate.postForEntity(
                "/api/auth/login",
                request,
                LoginResponseDto.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().getToken();
    }

    private ResponseEntity<String> updateProfile(
            String token,
            String currentPassword,
            String newPassword,
            String nickname) {
        Map<String, String> request = new HashMap<>();
        request.put("currentPw", currentPassword);
        request.put("newPw", newPassword);
        if (nickname != null) {
            request.put("nickname", nickname);
        }
        return restTemplate.exchange(
                "/api/users/me",
                HttpMethod.PUT,
                new HttpEntity<>(request, bearerHeaders(token)),
                String.class
        );
    }

    private ResponseEntity<String> protectedProfile(String token) {
        return restTemplate.exchange(
                "/api/users/me",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)),
                String.class
        );
    }

    private ResponseEntity<String> adminInquiries(String token) {
        return restTemplate.exchange(
                "/api/admin/inquiries",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)),
                String.class
        );
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private int authVersion() {
        return jdbcTemplate.queryForObject(
                "select auth_version from users where user_id = ?",
                Integer.class,
                PRIMARY_USER_ID
        );
    }

    private String passwordHash() {
        return jdbcTemplate.queryForObject(
                "select password from users where user_id = ?",
                String.class,
                PRIMARY_USER_ID
        );
    }

    private void deleteFixtures() {
        jdbcTemplate.update("delete from address where user_id in (?, ?)", PRIMARY_USER_ID, SECONDARY_USER_ID);
        jdbcTemplate.update("delete from point_account where user_id in (?, ?)", PRIMARY_USER_ID, SECONDARY_USER_ID);
        jdbcTemplate.update("delete from users where user_id in (?, ?)", PRIMARY_USER_ID, SECONDARY_USER_ID);
    }
}
