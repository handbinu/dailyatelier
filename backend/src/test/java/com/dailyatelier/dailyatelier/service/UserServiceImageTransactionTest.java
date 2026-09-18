package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.CloudinaryUploadResult;
import com.dailyatelier.dailyatelier.entity.PointAccount;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.jwt.JwtTokenProvider;
import com.dailyatelier.dailyatelier.repository.CloudinaryCleanupRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-image-transaction-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(UserService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserServiceImageTransactionTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CloudinaryCleanupRepository cloudinaryCleanupRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private CloudinaryService cloudinaryService;

    @MockitoBean
    private PointAccountService pointAccountService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void cleanUp() {
        cloudinaryCleanupRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void rollsBackProfileReferenceAndCleanupRegistrationTogether() {
        User user = createUserWithProfile();
        userRepository.saveAndFlush(user);

        MockMultipartFile image = new MockMultipartFile(
                "image", "profile.png", "image/png", new byte[]{1, 2, 3}
        );
        when(cloudinaryService.uploadProfileImage("member", image))
                .thenReturn(new CloudinaryUploadResult(
                        "https://res.cloudinary.com/test/new-profile.png",
                        "profiles/member/new-profile"
                ));
        when(pointAccountService.getAccount("member"))
                .thenReturn(PointAccount.open(user, 0L, LocalDateTime.now()));

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            userService.updateProfileImage("member", image);
            status.setRollbackOnly();
        });

        User reloaded = userRepository.findById("member").orElseThrow();
        assertThat(reloaded.getProfileImageUrl())
                .isEqualTo("https://res.cloudinary.com/test/old-profile.png");
        assertThat(reloaded.getProfileImagePublicId())
                .isEqualTo("profiles/member/old-profile");
        assertThat(cloudinaryCleanupRepository.count()).isZero();
    }

    @Test
    void serializesConcurrentProfileReplacementsWithoutDuplicateCleanup() throws Exception {
        User user = createUserWithProfile();
        userRepository.saveAndFlush(user);
        MockMultipartFile firstImage = new MockMultipartFile(
                "image", "first.png", "image/png", new byte[]{1}
        );
        MockMultipartFile secondImage = new MockMultipartFile(
                "image", "second.png", "image/png", new byte[]{2}
        );
        CountDownLatch uploadsReady = new CountDownLatch(2);
        CountDownLatch releaseUploads = new CountDownLatch(1);
        when(cloudinaryService.uploadProfileImage(
                org.mockito.ArgumentMatchers.eq("member"),
                org.mockito.ArgumentMatchers.any()
        )).thenAnswer(invocation -> {
            MockMultipartFile uploadedFile = invocation.getArgument(1);
            uploadsReady.countDown();
            if (!releaseUploads.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 업로드 대기 시간 초과");
            }
            String name = uploadedFile.getOriginalFilename().replace(".png", "");
            return new CloudinaryUploadResult(
                    "https://res.cloudinary.com/test/" + name + ".png",
                    "profiles/member/" + name
            );
        });
        when(pointAccountService.getAccount("member"))
                .thenReturn(PointAccount.open(user, 0L, LocalDateTime.now()));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(
                    () -> userService.updateProfileImage("member", firstImage)
            );
            Future<?> second = executor.submit(
                    () -> userService.updateProfileImage("member", secondImage)
            );
            assertThat(uploadsReady.await(5, TimeUnit.SECONDS)).isTrue();
            releaseUploads.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        User reloaded = userRepository.findById("member").orElseThrow();
        Set<String> cleanupPublicIds = cloudinaryCleanupRepository.findAll().stream()
                .map(cleanup -> cleanup.getPublicId())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(cleanupPublicIds).hasSize(2)
                .contains("profiles/member/old-profile");
        assertThat(cleanupPublicIds)
                .doesNotContain(reloaded.getProfileImagePublicId());
        assertThat(Set.of(
                reloaded.getProfileImagePublicId(),
                cleanupPublicIds.stream()
                        .filter(id -> !id.equals("profiles/member/old-profile"))
                        .findFirst()
                        .orElseThrow()
        )).containsExactlyInAnyOrder(
                "profiles/member/first",
                "profiles/member/second"
        );
    }

    private User createUserWithProfile() {
        User user = new User();
        user.setUserId("member");
        user.setPassword("encoded-password");
        user.setName("회원");
        user.setNickname("테스트");
        user.setPhoneNumber("010-0000-0000");
        user.setEmail("member@example.com");
        user.setJoinDate(LocalDateTime.of(2026, 7, 28, 15, 0));
        user.setUserStatus(0);
        user.setProfileImageUrl("https://res.cloudinary.com/test/old-profile.png");
        user.setProfileImagePublicId("profiles/member/old-profile");
        return user;
    }
}
