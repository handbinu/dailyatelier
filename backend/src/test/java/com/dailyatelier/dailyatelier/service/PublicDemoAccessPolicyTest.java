package com.dailyatelier.dailyatelier.service;

import com.dailyatelier.dailyatelier.exception.DomainApiException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicDemoAccessPolicyTest {

    @Test
    void keepsExistingRegistrationAndSignatureFlowsWhenPublicDemoIsDisabled() {
        PublicDemoAccessPolicy policy = policy(false, "", 1);

        policy.assertArtistRegistrationAllowed();
        policy.assertCloudinarySignatureAllowed("any-artist");
        policy.assertCloudinarySignatureAllowed("any-artist");
    }

    @Test
    void rejectsNewArtistRegistrationWhenPublicDemoIsEnabled() {
        PublicDemoAccessPolicy policy = policy(true, "demo-artist", 1);

        assertThatThrownBy(policy::assertArtistRegistrationAllowed)
                .isInstanceOf(DomainApiException.class)
                .satisfies(error -> assertThat(((DomainApiException) error).getCode())
                        .isEqualTo("PUBLIC_DEMO_ARTIST_REGISTRATION_DISABLED"));
    }

    @Test
    void allowsOnlyConfiguredDemoArtistAndLimitsSignatureIssuance() {
        PublicDemoAccessPolicy policy = policy(true, "demo-artist", 1);

        assertThatThrownBy(() -> policy.assertCloudinarySignatureAllowed("other-artist"))
                .isInstanceOf(DomainApiException.class)
                .satisfies(error -> assertThat(((DomainApiException) error).getCode())
                        .isEqualTo("PUBLIC_DEMO_UPLOAD_FORBIDDEN"));

        policy.assertCloudinarySignatureAllowed("demo-artist");

        assertThatThrownBy(() -> policy.assertCloudinarySignatureAllowed("demo-artist"))
                .isInstanceOf(DomainApiException.class)
                .satisfies(error -> assertThat(((DomainApiException) error).getCode())
                        .isEqualTo("PUBLIC_DEMO_UPLOAD_RATE_LIMITED"));
    }

    private PublicDemoAccessPolicy policy(boolean enabled, String artistIds, int maxAttempts) {
        return new PublicDemoAccessPolicy(
                enabled,
                artistIds,
                maxAttempts,
                600,
                Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC)
        );
    }
}
