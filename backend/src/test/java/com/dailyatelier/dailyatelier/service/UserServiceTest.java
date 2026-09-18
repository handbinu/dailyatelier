package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.dto.UserProfileDto;
import com.dailyatelier.dailyatelier.dto.ProfileUpdateDto;
import com.dailyatelier.dailyatelier.dto.CloudinaryUploadResult;
import com.dailyatelier.dailyatelier.entity.PointAccount;
import com.dailyatelier.dailyatelier.entity.CloudinaryCleanup;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.exception.DomainApiException;
import com.dailyatelier.dailyatelier.repository.AddressRepository;
import com.dailyatelier.dailyatelier.repository.ArtistRepository;
import com.dailyatelier.dailyatelier.repository.CloudinaryCleanupRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
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
    @Mock
    private CloudinaryCleanupRepository cloudinaryCleanupRepository;

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
        lenient().when(userRepository.findByIdForUpdate("member"))
                .thenReturn(Optional.of(user));
        lenient().when(userRepository.existsById("member")).thenReturn(true);
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
        String uploadedPublicId = "profiles/member/new-profile";
        when(userRepository.findByUserId("member")).thenReturn(user);
        when(cloudinaryService.uploadProfileImage("member", image))
                .thenReturn(new CloudinaryUploadResult(uploadedUrl, uploadedPublicId));
        when(pointAccountService.getAccount("member"))
                .thenReturn(PointAccount.open(user, 0L, LocalDateTime.now()));

        UserProfileDto response = userService.updateProfileImage("member", image);

        assertThat(user.getProfileImageUrl()).isEqualTo(uploadedUrl);
        assertThat(user.getProfileImagePublicId()).isEqualTo(uploadedPublicId);
        assertThat(response.getProfileImageUrl()).isEqualTo(uploadedUrl);
        verify(userRepository).save(user);
        verify(cloudinaryCleanupRepository, never()).save(any());
    }

    @Test
    void registersPreviousProfileImageWhenImageChanges() {
        user.setProfileImageUrl("https://res.cloudinary.com/demo/old-profile.png");
        user.setProfileImagePublicId("profiles/member/old-profile");
        when(userRepository.findByUserId("member")).thenReturn(user);
        when(cloudinaryService.uploadProfileImage("member", image))
                .thenReturn(new CloudinaryUploadResult(
                        "https://res.cloudinary.com/demo/new-profile.png",
                        "profiles/member/new-profile"
                ));
        when(pointAccountService.getAccount("member"))
                .thenReturn(PointAccount.open(user, 0L, LocalDateTime.now()));

        userService.updateProfileImage("member", image);

        org.mockito.ArgumentCaptor<CloudinaryCleanup> cleanup =
                org.mockito.ArgumentCaptor.forClass(CloudinaryCleanup.class);
        verify(cloudinaryCleanupRepository).save(cleanup.capture());
        assertThat(cleanup.getValue().getPublicId())
                .isEqualTo("profiles/member/old-profile");
        assertThat(cleanup.getValue().getResourceType()).isEqualTo("image");
    }

    @Test
    void doesNotRegisterProfileCleanupWhenPublicIdDoesNotChange() {
        user.setProfileImagePublicId("profiles/member/profile");
        when(userRepository.findByUserId("member")).thenReturn(user);
        when(cloudinaryService.uploadProfileImage("member", image))
                .thenReturn(new CloudinaryUploadResult(
                        "https://res.cloudinary.com/demo/profile.png",
                        "profiles/member/profile"
                ));
        when(pointAccountService.getAccount("member"))
                .thenReturn(PointAccount.open(user, 0L, LocalDateTime.now()));

        userService.updateProfileImage("member", image);

        verify(cloudinaryCleanupRepository, never()).save(any());
    }

    @Test
    void treatsExistingActiveProfileCleanupAsSuccessfulRegistration() {
        user.setProfileImagePublicId("profiles/member/old-profile");
        when(userRepository.findByUserId("member")).thenReturn(user);
        when(cloudinaryService.uploadProfileImage("member", image))
                .thenReturn(new CloudinaryUploadResult(
                        "https://res.cloudinary.com/demo/new-profile.png",
                        "profiles/member/new-profile"
                ));
        when(cloudinaryCleanupRepository.existsByPublicIdAndStatusIn(
                org.mockito.ArgumentMatchers.eq("profiles/member/old-profile"),
                any()
        )).thenReturn(true);
        when(pointAccountService.getAccount("member"))
                .thenReturn(PointAccount.open(user, 0L, LocalDateTime.now()));

        UserProfileDto response = userService.updateProfileImage("member", image);

        assertThat(response.getProfileImageUrl())
                .isEqualTo("https://res.cloudinary.com/demo/new-profile.png");
        verify(cloudinaryCleanupRepository, never()).save(any());
    }

    @Test
    void keepsExistingUrlWhenCloudinaryUploadFails() {
        user.setProfileImageUrl("https://res.cloudinary.com/demo/old-profile.png");
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
        assertThatThrownBy(() -> userService.updateProfileImage("missing", image))
                .isInstanceOf(DomainApiException.class)
                .satisfies(exception -> {
                    DomainApiException domainException = (DomainApiException) exception;
                    assertThat(domainException.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(domainException.getCode()).isEqualTo("USER_NOT_FOUND");
                });

        verify(cloudinaryService, never()).uploadProfileImage("missing", image);
    }

    @Test
    void storesNameAndReturnsItInSubsequentProfileQuery() {
        ProfileUpdateDto update = new ProfileUpdateDto();
        update.setName("변경된 이름");
        when(userRepository.findByUserId("member")).thenReturn(user);
        when(pointAccountService.getAccount("member"))
                .thenReturn(PointAccount.open(user, 0L, LocalDateTime.now()));

        userService.updateUserProfile("member", update);

        assertThat(user.getName()).isEqualTo("변경된 이름");
        UserProfileDto response = userService.getUserProfile("member");
        assertThat(response.getName()).isEqualTo("변경된 이름");
        verify(userRepository).save(user);
    }
}
