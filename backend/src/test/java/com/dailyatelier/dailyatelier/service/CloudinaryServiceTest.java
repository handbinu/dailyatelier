package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.exception.DomainApiException;
import com.dailyatelier.dailyatelier.dto.CloudinaryUploadResult;
import com.dailyatelier.dailyatelier.entity.User;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CloudinaryServiceTest {

    private CloudinaryService cloudinaryService;
    private MockRestServiceServer server;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        userRepository = mock(UserRepository.class);
        cloudinaryService = new CloudinaryService(userRepository, builder);
        ReflectionTestUtils.setField(cloudinaryService, "cloudName", "test-cloud");
        ReflectionTestUtils.setField(cloudinaryService, "apiKey", "test-key");
        ReflectionTestUtils.setField(cloudinaryService, "apiSecret", "test-secret");
    }

    @Test
    void uploadsValidProfileImageAndReturnsCloudinaryReference() {
        server.expect(requestTo("https://api.cloudinary.com/v1_1/test-cloud/image/upload"))
                .andRespond(withSuccess(
                        "{\"secure_url\":\"https://res.cloudinary.com/test/profile.png\","
                                + "\"public_id\":\"profiles/member/profile\"}",
                        MediaType.APPLICATION_JSON
                ));
        MockMultipartFile image = new MockMultipartFile(
                "image", "profile.png", "image/png", new byte[]{1, 2, 3}
        );

        CloudinaryUploadResult result = cloudinaryService.uploadProfileImage("member", image);

        assertThat(result.secureUrl()).isEqualTo("https://res.cloudinary.com/test/profile.png");
        assertThat(result.publicId()).isEqualTo("profiles/member/profile");
        server.verify();
    }

    @Test
    void createsArtistUploadSignatureForUserNamespace() {
        User artist = new User();
        artist.setUserStatus(1);
        when(userRepository.findByUserId("artist1")).thenReturn(artist);

        assertThat(cloudinaryService.createUploadSignature("artist1", "arts").getFolder())
                .isEqualTo("arts/artist1");
    }

    @Test
    void validatesMatchingArtUrlAndPublicId() {
        cloudinaryService.validateArtImageReference(
                "artist1",
                "https://res.cloudinary.com/test-cloud/image/upload/v123/arts/artist1/work.jpg",
                "arts/artist1/work"
        );
    }

    @Test
    void rejectsForeignArtImageNamespace() {
        assertThatThrownBy(() -> cloudinaryService.validateArtImageReference(
                "artist1",
                "https://res.cloudinary.com/test-cloud/image/upload/v123/arts/other/work.jpg",
                "arts/other/work"
        )).isInstanceOf(DomainApiException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://res.cloudinary.com/test-cloud/image/upload/v123/arts/artist1/work.jpg",
            "https://res.cloudinary.com.evil.example/test-cloud/image/upload/v123/arts/artist1/work.jpg",
            "https://res.cloudinary.com/other-cloud/image/upload/v123/arts/artist1/work.jpg",
            "https://res.cloudinary.com/test-cloud/image/upload/v123/arts/artist1/work.jpg?download=1",
            "https://res.cloudinary.com/test-cloud/image/upload/v123/arts/artist1/work.jpg#fragment",
            "https://attacker@res.cloudinary.com/test-cloud/image/upload/v123/arts/artist1/work.jpg",
            "https://res.cloudinary.com:443/test-cloud/image/upload/v123/arts/artist1/work.jpg",
            "https://res.cloudinary.com/test-cloud/image/upload/arts/artist1/work.jpg",
            "https://res.cloudinary.com/test-cloud/image/upload/version123/arts/artist1/work.jpg",
            "https://res.cloudinary.com/test-cloud/image/upload/v123/arts/artist10/work.jpg",
            "https://res.cloudinary.com/test-cloud/image/upload/c_fill,w_100/v123/arts/artist1/work.jpg"
    })
    void rejectsUnsafeOrUnsupportedArtImageUrl(String secureUrl) {
        assertInvalidArtImageReference(
                secureUrl,
                "arts/artist1/work"
        );
    }

    @Test
    void rejectsArtUrlThatPointsToDifferentPublicId() {
        assertInvalidArtImageReference(
                "https://res.cloudinary.com/test-cloud/image/upload/v123/arts/artist1/work.jpg",
                "arts/artist1/other"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"secure_url\":\"https://res.cloudinary.com/test-cloud/image/upload/v1/profiles/member/profile.png\"}",
            "{\"secure_url\":\"https://res.cloudinary.com/test-cloud/image/upload/v1/profiles/member/profile.png\",\"public_id\":\"   \"}",
            "{\"secure_url\":\"https://res.cloudinary.com/test-cloud/image/upload/v1/profiles/other/profile.png\",\"public_id\":\"profiles/other/profile\"}",
            "{\"secure_url\":\"https://res.cloudinary.com/test-cloud/image/upload/v1/profiles/member2/profile.png\",\"public_id\":\"profiles/member2/profile\"}"
    })
    void rejectsProfileUploadResponseWithoutExactUserNamespace(String responseBody) {
        server.expect(requestTo("https://api.cloudinary.com/v1_1/test-cloud/image/upload"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
        MockMultipartFile image = new MockMultipartFile(
                "image", "profile.png", "image/png", new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> cloudinaryService.uploadProfileImage("member", image))
                .isInstanceOf(DomainApiException.class)
                .satisfies(error -> assertThat(((DomainApiException) error).getCode())
                        .isEqualTo("PROFILE_IMAGE_UPLOAD_FAILED"));

        server.verify();
    }

    @Test
    void rejectsUnsupportedProfileImageTypeAsBadRequest() {
        MockMultipartFile image = new MockMultipartFile(
                "image", "profile.webp", "image/webp", new byte[]{1}
        );

        assertThatThrownBy(() -> cloudinaryService.uploadProfileImage("member", image))
                .isInstanceOf(DomainApiException.class)
                .satisfies(exception -> {
                    DomainApiException domainException = (DomainApiException) exception;
                    assertThat(domainException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(domainException.getCode()).isEqualTo("INVALID_PROFILE_IMAGE_TYPE");
                });
    }

    @Test
    void rejectsOversizedProfileImageAsBadRequest() {
        MockMultipartFile image = new MockMultipartFile(
                "image", "profile.png", "image/png", new byte[5 * 1024 * 1024 + 1]
        );

        assertThatThrownBy(() -> cloudinaryService.uploadProfileImage("member", image))
                .isInstanceOf(DomainApiException.class)
                .satisfies(exception -> {
                    DomainApiException domainException = (DomainApiException) exception;
                    assertThat(domainException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(domainException.getCode()).isEqualTo("PROFILE_IMAGE_TOO_LARGE");
                });
    }

    @Test
    void reportsCloudinaryFailureAsBadGateway() {
        server.expect(requestTo("https://api.cloudinary.com/v1_1/test-cloud/image/upload"))
                .andRespond(withServerError());
        MockMultipartFile image = new MockMultipartFile(
                "image", "profile.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> cloudinaryService.uploadProfileImage("member", image))
                .isInstanceOf(DomainApiException.class)
                .satisfies(exception -> {
                    DomainApiException domainException = (DomainApiException) exception;
                    assertThat(domainException.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(domainException.getCode()).isEqualTo("PROFILE_IMAGE_UPLOAD_FAILED");
                });

        server.verify();
    }

    private void assertInvalidArtImageReference(String secureUrl, String publicId) {
        assertThatThrownBy(() -> cloudinaryService.validateArtImageReference(
                "artist1",
                secureUrl,
                publicId
        )).isInstanceOf(DomainApiException.class)
                .satisfies(error -> assertThat(((DomainApiException) error).getCode())
                        .isEqualTo("INVALID_ART_IMAGE_REFERENCE"));
    }
}
