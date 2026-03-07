package com.devpick.global.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String BASE64_SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci1kZXZwaWNrLXRlc3Rpbmc=";
    private static final long ACCESS_TOKEN_EXPIRY = 3_600_000L;    // 1시간
    private static final long REFRESH_TOKEN_EXPIRY = 604_800_000L; // 7일

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(BASE64_SECRET, ACCESS_TOKEN_EXPIRY, REFRESH_TOKEN_EXPIRY);
    }

    @Test
    @DisplayName("generateAccessToken - UUID로 Access Token 생성 시 null이 아닌 JWT 문자열을 반환한다")
    void generateAccessToken_returnsNonNullToken() {
        UUID userId = UUID.randomUUID();

        String token = jwtTokenProvider.generateAccessToken(userId);

        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("extractUserId - Access Token에서 userId 추출 시 원래 UUID와 일치한다")
    void extractUserId_returnsCorrectUserId() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenProvider.generateAccessToken(userId);

        UUID extracted = jwtTokenProvider.extractUserId(token);

        assertThat(extracted).isEqualTo(userId);
    }

    @Test
    @DisplayName("isTokenValid - 유효한 Access Token은 true를 반환한다")
    void isTokenValid_withValidToken_returnsTrue() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenProvider.generateAccessToken(userId);

        assertThat(jwtTokenProvider.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid - 잘못된 토큰 문자열은 false를 반환한다")
    void isTokenValid_withInvalidToken_returnsFalse() {
        assertThat(jwtTokenProvider.isTokenValid("invalid.token.string")).isFalse();
    }

    @Test
    @DisplayName("generateRefreshToken - Refresh Token 생성 시 null이 아닌 문자열을 반환한다")
    void generateRefreshToken_returnsNonNullToken() {
        String refreshToken = jwtTokenProvider.generateRefreshToken();

        assertThat(refreshToken).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("getRefreshTokenExpiresAt - Refresh Token 만료 시각은 현재 시각보다 미래여야 한다")
    void getRefreshTokenExpiresAt_returnsFutureDateTime() {
        LocalDateTime expiresAt = jwtTokenProvider.getRefreshTokenExpiresAt();

        assertThat(expiresAt).isAfter(LocalDateTime.now());
    }
}
