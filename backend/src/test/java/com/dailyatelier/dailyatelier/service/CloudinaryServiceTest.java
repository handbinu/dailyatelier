package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.exception.DomainApiException;
import com.dailyatelier.dailyatelier.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CloudinaryServiceTest {

    private CloudinaryService cloudinaryService;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        cloudinaryService = new CloudinaryService(mock(UserRepository.class), builder);
        ReflectionTestUtils.setField(cloudinaryService, "cloudName", "test-cloud");
        ReflectionTestUtils.setField(cloudinaryService, "apiKey", "test-key");
        ReflectionTestUtils.setField(cloudinaryService, "apiSecret", "test-secret");
    }

    @Test
    void uploadsValidProfileImageAndReturnsCloudinaryUrl() {
        server.expect(requestTo("https://api.cloudinary.com/v1_1/test-cloud/image/upload"))
                .andRespond(withSuccess(
                        "{\"secure_url\":\"https://res.cloudinary.com/test/profile.png\"}",
                        MediaType.APPLICATION_JSON
                ));
        MockMultipartFile image = new MockMultipartFile(
                "image", "profile.png", "image/png", new byte[]{1, 2, 3}
        );

        String result = cloudinaryService.uploadProfileImage("member", image);

        assertThat(result).isEqualTo("https://res.cloudinary.com/test/profile.png");
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
}
