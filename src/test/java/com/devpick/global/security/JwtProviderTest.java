package com.devpick.global.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private JwtProvider jwtProvider;
    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final String TEST_EMAIL = "test@devpick.kr";

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder()
                .encodeToString("test-jwt-secret-key-123456789012".getBytes());
        jwtProvider = new JwtProvider(secret, 3600000L);
    }

    @Test
    @DisplayName("createAccessToken — userId, email을 포함한 JWT 토큰 생성")
    void createAccessToken_success() {
        String token = jwtProvider.createAccessToken(TEST_USER_ID, TEST_EMAIL);

        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("parseClaims — 토큰에서 userId, email 추출")
    void parseClaims_extractsSubjectAndEmail() {
        String token = jwtProvider.createAccessToken(TEST_USER_ID, TEST_EMAIL);

        Claims claims = jwtProvider.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo(TEST_USER_ID.toString());
        assertThat(claims.get("email", String.class)).isEqualTo(TEST_EMAIL);
    }

    @Test
    @DisplayName("validateToken — 유효한 토큰은 true 반환")
    void validateToken_validToken_returnsTrue() {
        String token = jwtProvider.createAccessToken(TEST_USER_ID, TEST_EMAIL);

        assertThat(jwtProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("validateToken — 만료된 토큰은 false 반환")
    void validateToken_expiredToken_returnsFalse() {
        String secret = Base64.getEncoder()
                .encodeToString("test-jwt-secret-key-123456789012".getBytes());
        JwtProvider expiredProvider = new JwtProvider(secret, -1L);
        String token = expiredProvider.createAccessToken(TEST_USER_ID, TEST_EMAIL);

        assertThat(jwtProvider.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("validateToken — 위조된 토큰은 false 반환")
    void validateToken_tamperedToken_returnsFalse() {
        assertThat(jwtProvider.validateToken("invalid.token.string")).isFalse();
    }

    @Test
    @DisplayName("validateToken — 빈 문자열은 false 반환")
    void validateToken_emptyString_returnsFalse() {
        assertThat(jwtProvider.validateToken("")).isFalse();
    }
}
