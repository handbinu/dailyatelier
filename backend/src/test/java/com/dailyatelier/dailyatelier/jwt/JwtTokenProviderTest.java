package com.dailyatelier.dailyatelier.jwt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(
            "test-secret-key-test-secret-key-test-secret-key", 60_000
    );

    @Test
    void includesAuthenticationVersionInNewTokens() {
        String token = tokenProvider.generateToken("member", 1, 3);

        assertThat(tokenProvider.validateToken(token)).isTrue();
        assertThat(tokenProvider.getUserId(token)).isEqualTo("member");
        assertThat(tokenProvider.getAuthVersion(token)).isEqualTo(3);
    }
}
