package com.dailyatelier.dailyatelier.loadtest;

import com.dailyatelier.dailyatelier.dto.LoginRequestDto;
import com.dailyatelier.dailyatelier.dto.LoginResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate"
        }
)
@EnabledIfEnvironmentVariable(
        named = "DAILYATELIER_LOAD_TEST_FIXTURE",
        matches = "true"
)
class LoadTestFixtureMySqlTest {

    private static final Pattern ALLOWED_DATABASE = Pattern.compile(
            "dailyatelier_load_test(?:_[a-z0-9_]+)?"
    );
    private static final String MARKER = "dailyatelier-load-test-v1";
    private static final String SELLER_ID = "load_seller";
    private static final String ARTIST_CODE = "00000000-0000-0000-0000-000000000001";
    private static final int BIDDER_COUNT = 32;
    private static final int DISTRIBUTED_ART_COUNT = 32;
    private static final int SHARED_ACCOUNT_ART_COUNT = 8;
    private static final long INITIAL_BALANCE = 10_000_000_000L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void preparesIsolatedFixtureAndVerifiesLoginAndHttpBid() throws Exception {
        String databaseName = currentDatabaseName();
        assertThat(databaseName).matches(ALLOWED_DATABASE);
        assertThat(tableCount("flyway_schema_history")).isEqualTo(1);
        assertThat(appliedMigrationCount()).isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject("select count(*) from users", Integer.class))
                .isZero();

        createGuardMarker();
        String fixturePassword = requiredFixturePassword();
        insertUsersAndAccounts(fixturePassword);
        insertArtist();
        insertArts();

        verifyFixtureState();
        Map<String, String> tokens = loginAllUsers(fixturePassword);
        verifyHttpBid(tokens.get(bidderId(1)));
        verifySmokeState();
    }

    private String currentDatabaseName() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String databaseName = connection.getCatalog();
            assertThat(connection.getMetaData().getURL())
                    .matches("jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):.*")
                    .contains("/" + databaseName);
            return databaseName;
        }
    }

    private int tableCount(String tableName) {
        return jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where table_schema = database()
                  and table_name = ?
                """,
                Integer.class,
                tableName
        );
    }

    private int appliedMigrationCount() {
        return jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = 1",
                Integer.class
        );
    }

    private void createGuardMarker() {
        jdbcTemplate.execute("""
                create table load_test_schema_guard (
                    marker varchar(64) primary key,
                    created_at datetime(6) not null
                ) engine = InnoDB
                """);
        jdbcTemplate.update(
                "insert into load_test_schema_guard (marker, created_at) values (?, now(6))",
                MARKER
        );
    }

    private String requiredFixturePassword() {
        String password = System.getenv("LOAD_TEST_FIXTURE_PASSWORD");
        assertThat(password)
                .as("LOAD_TEST_FIXTURE_PASSWORD")
                .isNotBlank();
        return password;
    }

    private void insertUsersAndAccounts(String fixturePassword) {
        String passwordHash = passwordEncoder.encode(fixturePassword);
        insertUser(SELLER_ID, "loadselle", "load-seller@example.test", 1, passwordHash);
        for (int index = 1; index <= BIDDER_COUNT; index++) {
            String userId = bidderId(index);
            insertUser(
                    userId,
                    "lb" + String.format("%03d", index),
                    userId + "@example.test",
                    0,
                    passwordHash
            );
            jdbcTemplate.update("""
                    insert into point_account (
                        user_id, available_balance, held_balance, version,
                        created_at, updated_at
                    ) values (?, ?, 0, 0, now(6), now(6))
                    """, userId, INITIAL_BALANCE);
            jdbcTemplate.update("""
                    insert into point_transaction (
                        user_id, type, amount, available_delta, held_delta,
                        available_balance_after, held_balance_after,
                        reference_type, reference_id, idempotency_key,
                        reversal_of_transaction_id, reason_code, description, created_at
                    ) values (?, 'OPENING_BALANCE', ?, ?, 0, ?, 0,
                              'USER', ?, ?, null, 'LOAD_TEST_FIXTURE',
                              '부하 테스트 초기 잔액', now(6))
                    """,
                    userId,
                    INITIAL_BALANCE,
                    INITIAL_BALANCE,
                    INITIAL_BALANCE,
                    userId,
                    "load-test:opening:" + userId
            );
        }
    }

    private void insertUser(
            String userId,
            String nickname,
            String email,
            int userStatus,
            String passwordHash) {
        jdbcTemplate.update("""
                insert into users (
                    user_id, password, name, nickname, phone_number, email,
                    join_date, user_status, reserve, email_agree, profile_image_url
                ) values (?, ?, 'Load Test User', ?, '010-0000-0000', ?,
                          now(6), ?, 0, 0, null)
                """, userId, passwordHash, nickname, email, userStatus);
    }

    private void insertArtist() {
        jdbcTemplate.update("""
                insert into artist (
                    artist_code, user_id, artist_name, artist_intro, homepage, artist_sns
                ) values (?, ?, 'Load Test Artist', null, null, null)
                """, ARTIST_CODE, SELLER_ID);
    }

    private void insertArts() {
        insertArt("LT-HOT-001");
        insertArt("LT-SMOKE-001");
        for (int index = 1; index <= DISTRIBUTED_ART_COUNT; index++) {
            insertArt("LT-DIST-" + String.format("%03d", index));
        }
        for (int index = 1; index <= SHARED_ACCOUNT_ART_COUNT; index++) {
            insertArt("LT-SHARED-" + String.format("%03d", index));
        }
    }

    private void insertArt(String name) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                insert into art (
                    artist_code, name, descript, material, format, category, w_intro,
                    start_price, current_price, minimum_bid_increment,
                    bid_start_time, closing_time, img_path, art_status,
                    winning_bid_id, active_point_hold_id, closed_at, created_at
                ) values (?, ?, 'load-test fixture', null, 'PHYSICAL', 'OTHER', null,
                          100000, 100000, 1000, ?, ?, 'load-test://placeholder', 0,
                          null, null, null, ?)
                """,
                ARTIST_CODE,
                name,
                now.minusHours(1),
                now.plusDays(7),
                now
        );
    }

    private void verifyFixtureState() {
        assertThat(jdbcTemplate.queryForObject("select count(*) from users", Integer.class))
                .isEqualTo(BIDDER_COUNT + 1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from point_account", Integer.class))
                .isEqualTo(BIDDER_COUNT);
        assertThat(jdbcTemplate.queryForObject("select count(*) from art", Integer.class))
                .isEqualTo(2 + DISTRIBUTED_ART_COUNT + SHARED_ACCOUNT_ART_COUNT);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from art
                where art_status = 0
                  and bid_start_time <= now(6)
                  and closing_time > now(6)
                  and artist_code = ?
                  and img_path = 'load-test://placeholder'
                """, Integer.class, ARTIST_CODE))
                .isEqualTo(2 + DISTRIBUTED_ART_COUNT + SHARED_ACCOUNT_ART_COUNT);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from point_account pa
                join users u on u.user_id = pa.user_id
                where u.user_status = 0
                  and pa.available_balance = ?
                  and pa.held_balance = 0
                """, Integer.class, INITIAL_BALANCE))
                .isEqualTo(BIDDER_COUNT);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from art a
                join artist ar on ar.artist_code = a.artist_code
                join users u on u.user_id = ar.user_id
                where u.user_id = ?
                """, Integer.class, SELLER_ID))
                .isEqualTo(2 + DISTRIBUTED_ART_COUNT + SHARED_ACCOUNT_ART_COUNT);
    }

    private Map<String, String> loginAllUsers(String fixturePassword) {
        java.util.LinkedHashMap<String, String> tokens = new java.util.LinkedHashMap<>();
        tokens.put(SELLER_ID, login(SELLER_ID, fixturePassword));
        for (int index = 1; index <= BIDDER_COUNT; index++) {
            String userId = bidderId(index);
            tokens.put(userId, login(userId, fixturePassword));
        }
        assertThat(tokens).hasSize(BIDDER_COUNT + 1);
        assertThat(tokens.values()).allSatisfy(token -> assertThat(token).isNotBlank());
        return tokens;
    }

    private String login(String userId, String fixturePassword) {
        LoginRequestDto request = new LoginRequestDto();
        request.setUserId(userId);
        request.setPassword(fixturePassword);
        ResponseEntity<LoginResponseDto> response = restTemplate.postForEntity(
                "/api/auth/login",
                request,
                LoginResponseDto.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUserId()).isEqualTo(userId);
        return response.getBody().getToken();
    }

    private void verifyHttpBid(String token) {
        Long smokeArtId = jdbcTemplate.queryForObject(
                "select art_id from art where name = 'LT-SMOKE-001'",
                Long.class
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Integer>> request = new HttpEntity<>(
                Map.of("bidPrice", 101_000),
                headers
        );
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/arts/" + smokeArtId + "/bids",
                request,
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private void verifySmokeState() {
        assertThat(jdbcTemplate.queryForObject("select count(*) from bid", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from point_hold", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from point_transaction", Integer.class))
                .isEqualTo(BIDDER_COUNT + 1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from art a
                join point_hold ph on ph.hold_id = a.active_point_hold_id
                join bid b on b.bid_id = ph.latest_bid_id
                where a.name = 'LT-SMOKE-001'
                  and a.current_price = 101000
                  and ph.status = 'HELD'
                  and ph.user_id = ?
                  and ph.amount = 101000
                  and b.bid_price = 101000
                """, Integer.class, bidderId(1))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from point_account
                where user_id = ?
                  and available_balance = ?
                  and held_balance = 101000
                """, Integer.class, bidderId(1), INITIAL_BALANCE - 101_000L))
                .isEqualTo(1);
    }

    private String bidderId(int index) {
        return "load_bid_" + String.format("%03d", index);
    }
}
