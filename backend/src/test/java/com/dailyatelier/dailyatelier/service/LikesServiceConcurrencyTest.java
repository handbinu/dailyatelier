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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
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

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:likes-concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(LikesService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LikesServiceConcurrencyTest {
    private static final String USER_ID = "likes-concurrent-user";

    @Autowired
    private LikesService likesService;
    @Autowired
    private LikesRepository likesRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ArtRepository artRepository;

    private Long artId;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUserId(USER_ID);
        user.setPassword("password");
        user.setName("동시 찜 사용자");
        user.setNickname("동시찜");
        user.setPhoneNumber("010-0000-0000");
        user.setEmail("likes-concurrent@example.com");
        user.setJoinDate(LocalDateTime.of(2026, 10, 20, 12, 0));
        user.setUserStatus(0);
        userRepository.saveAndFlush(user);

        Art art = new Art();
        art.setName("동시 찜 작품");
        art.setFormat(ArtFormat.PHYSICAL);
        art.setCategory(ArtCategory.OTHER);
        art.setStartPrice(10_000);
        art.setCurrentPrice(10_000);
        art.setMinimumBidIncrement(1_000);
        art.setBidStartTime(LocalDateTime.of(2026, 10, 19, 12, 0));
        art.setClosingTime(LocalDateTime.of(2026, 10, 21, 12, 0));
        art.setImgPath("https://example.com/likes-concurrency.jpg");
        art.setArtStatus(Art.STATUS_ACTIVE);
        artId = artRepository.saveAndFlush(art).getArtId();
    }

    @AfterEach
    void tearDown() {
        likesRepository.deleteAll();
        artRepository.deleteAll();
        userRepository.deleteAll();
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

        assertThat(likesRepository.count()).isEqualTo(1L);
        assertThat(likesRepository.existsByUserAndArt(
                userRepository.findByUserId(USER_ID),
                artRepository.findById(artId).orElseThrow())).isTrue();
    }

    private boolean addLike(CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 찜 시작 대기 시간이 초과되었습니다");
            }
            return likesService.addLike(USER_ID, artId).isLiked();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
