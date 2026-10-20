package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.ArtCategory;
import com.dailyatelier.dailyatelier.entity.ArtFormat;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.repository.ArtRepository;
import com.dailyatelier.dailyatelier.repository.LikesRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.flyway.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(LikesService.class)
@EnabledIfEnvironmentVariable(
        named = "DAILYATELIER_MYSQL_SCHEMA_TEST",
        matches = "true"
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LikesMySqlConcurrencyTest {
    private static final String USER_ID = "mysql-likes-concurrent";

    @Autowired
    private LikesService likesService;
    @Autowired
    private LikesRepository likesRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ArtRepository artRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long artId;

    @BeforeEach
    void setUp() {
        deleteFixtures();
        User user = new User();
        user.setUserId(USER_ID);
        user.setPassword("password");
        user.setName("MySQL 동시 찜 사용자");
        user.setNickname("mysql찜");
        user.setPhoneNumber("010-0000-0000");
        user.setEmail("mysql-likes@example.com");
        user.setJoinDate(LocalDateTime.of(2026, 10, 20, 12, 0));
        user.setUserStatus(0);
        userRepository.saveAndFlush(user);

        Art art = new Art();
        art.setName("MySQL 동시 찜 작품");
        art.setFormat(ArtFormat.PHYSICAL);
        art.setCategory(ArtCategory.OTHER);
        art.setStartPrice(10_000);
        art.setCurrentPrice(10_000);
        art.setMinimumBidIncrement(1_000);
        art.setBidStartTime(LocalDateTime.of(2026, 10, 19, 12, 0));
        art.setClosingTime(LocalDateTime.of(2026, 10, 21, 12, 0));
        art.setImgPath("https://example.com/mysql-likes-concurrency.jpg");
        art.setArtStatus(Art.STATUS_ACTIVE);
        artId = artRepository.saveAndFlush(art).getArtId();
    }

    @AfterEach
    void tearDown() {
        deleteFixtures();
    }

    @Test
    void uniqueConstraintRejectsDuplicatePairAndAllowsDeletedArtHistory() {
        jdbcTemplate.update("insert into likes (user_id, art_id) values (?, ?)", USER_ID, artId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into likes (user_id, art_id) values (?, ?)", USER_ID, artId))
                .isInstanceOf(DataAccessException.class);

        jdbcTemplate.update("insert into likes (user_id, art_id) values (?, null)", USER_ID);
        jdbcTemplate.update("insert into likes (user_id, art_id) values (?, null)", USER_ID);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from likes where user_id = ?", Long.class, USER_ID))
                .isEqualTo(3L);
    }

    @Test
    void concurrentAddLikeReturnsLikedForBothRequestsAndStoresOneRow() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> addLike(ready, start)),
                    executor.submit(() -> addLike(ready, start))
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(results.get(0).get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(results.get(1).get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from likes where user_id = ? and art_id = ?",
                Long.class, USER_ID, artId)).isEqualTo(1L);
    }

    private boolean addLike(CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("MySQL 동시 찜 시작 대기 시간이 초과되었습니다");
            }
            return likesService.addLike(USER_ID, artId).isLiked();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void deleteFixtures() {
        jdbcTemplate.update("delete from likes where user_id = ?", USER_ID);
        jdbcTemplate.update("delete from art where name = ?", "MySQL 동시 찜 작품");
        jdbcTemplate.update("delete from users where user_id = ?", USER_ID);
    }
}
