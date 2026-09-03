package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.UserProfileDto;
import com.dailyatelier.dailyatelier.entity.PointAccount;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.exception.DomainApiException;
import com.dailyatelier.dailyatelier.repository.AddressRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ArtistRepository artistRepository;
    @Mock
    private AddressRepository addressRepository;
    @Mock
    private com.dailyatelier.dailyatelier.jwt.JwtTokenProvider jwtTokenProvider;
    @Mock
    private PointAccountService pointAccountService;
    @Mock
    private CloudinaryService cloudinaryService;

    @InjectMocks
    private UserService userService;

    private User user;
    private MockMultipartFile image;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUserId("member");
        user.setName("회원");
        user.setNickname("테스트");
        user.setPhoneNumber("010-0000-0000");
        user.setEmail("member@example.com");
        user.setUserStatus(0);
        image = new MockMultipartFile(
                "image", "profile.png", "image/png", new byte[]{1, 2, 3}
        );
    }

    @Test
    void returnsStoredProfileImageUrl() {
        user.setProfileImageUrl("https://res.cloudinary.com/demo/profile.png");
        when(userRepository.findByUserId("member")).thenReturn(user);
        when(pointAccountService.getAccount("member"))
                .thenReturn(PointAccount.open(user, 0L, LocalDateTime.now()));

        UserProfileDto response = userService.getUserProfile("member");

        assertThat(response.getProfileImageUrl()).isEqualTo(user.getProfileImageUrl());
    }

    @Test
    void uploadsAndStoresImageForAuthenticatedUser() {
        String uploadedUrl = "https://res.cloudinary.com/demo/new-profile.png";
        when(userRepository.findByUserId("member")).thenReturn(user);
        when(cloudinaryService.uploadProfileImage("member", image)).thenReturn(uploadedUrl);
        when(pointAccountService.getAccount("member"))
                .thenReturn(PointAccount.open(user, 0L, LocalDateTime.now()));

        UserProfileDto response = userService.updateProfileImage("member", image);

        assertThat(user.getProfileImageUrl()).isEqualTo(uploadedUrl);
        assertThat(response.getProfileImageUrl()).isEqualTo(uploadedUrl);
        verify(userRepository).save(user);
    }

    @Test
    void keepsExistingUrlWhenCloudinaryUploadFails() {
        user.setProfileImageUrl("https://res.cloudinary.com/demo/old-profile.png");
        when(userRepository.findByUserId("member")).thenReturn(user);
        when(cloudinaryService.uploadProfileImage("member", image)).thenThrow(
                new DomainApiException(
                        HttpStatus.BAD_GATEWAY,
                        "PROFILE_IMAGE_UPLOAD_FAILED",
                        "프로필 이미지 업로드에 실패했습니다."
                )
        );

        assertThatThrownBy(() -> userService.updateProfileImage("member", image))
                .isInstanceOf(DomainApiException.class)
                .satisfies(exception -> assertThat(((DomainApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.BAD_GATEWAY));

        assertThat(user.getProfileImageUrl())
                .isEqualTo("https://res.cloudinary.com/demo/old-profile.png");
        verify(userRepository, never()).save(user);
    }

    @Test
    void rejectsMissingAuthenticatedUserBeforeUpload() {
        when(userRepository.findByUserId("missing")).thenReturn(null);

        assertThatThrownBy(() -> userService.updateProfileImage("missing", image))
                .isInstanceOf(DomainApiException.class)
                .satisfies(exception -> {
                    DomainApiException domainException = (DomainApiException) exception;
                    assertThat(domainException.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(domainException.getCode()).isEqualTo("USER_NOT_FOUND");
                });

        verify(cloudinaryService, never()).uploadProfileImage("missing", image);
    }
}
