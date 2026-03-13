package com.devpick.domain.user.service;

import com.devpick.domain.user.dto.LoginResponse;
import com.devpick.domain.user.entity.RefreshToken;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.RefreshTokenRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.global.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @InjectMocks
    private TokenService tokenService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    // ── issueTokenPair ──────────────────────────────────────────────

    @Test
    @DisplayName("토큰 발급 - User 객체로 Access + Refresh Token 쌍이 발급되고 DB에 저장된다")
    void issueTokenPair_success() throws Exception {
        User user = createUser();
        given(jwtTokenProvider.generateAccessToken(user.getId())).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken()).willReturn("refresh-token");
        given(jwtTokenProvider.getRefreshTokenExpiresAt()).willReturn(LocalDateTime.now().plusDays(7));
        given(refreshTokenRepository.save(any(RefreshToken.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        LoginResponse response = tokenService.issueTokenPair(user);

        assertThat(response.accessToken()).isEqualTo("access-token");
        // refreshToken은 @JsonIgnore이지만 내부 값은 Cookie 설정용으로 유지
        assertThat(response.refreshTokenValue()).isEqualTo("refresh-token");
        verify(refreshTokenRepository).deleteByUser(user);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    // ── reissueTokens ──────────────────────────────────────────────

    @Test
    @DisplayName("토큰 재발급 - 유효한 Refresh Token으로 새 토큰 쌍이 발급된다")
    void reissueTokens_success() throws Exception {
        User user = createUser();
        RefreshToken stored = createRefreshToken(user, "old-refresh-token",
                LocalDateTime.now().plusDays(7));

        given(refreshTokenRepository.findByToken("old-refresh-token"))
                .willReturn(Optional.of(stored));
        given(jwtTokenProvider.generateAccessToken(user.getId()))
                .willReturn("new-access-token");
        given(jwtTokenProvider.generateRefreshToken())
                .willReturn("new-refresh-token");
        given(jwtTokenProvider.getRefreshTokenExpiresAt())
                .willReturn(LocalDateTime.now().plusDays(7));
        given(refreshTokenRepository.save(any(RefreshToken.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        String[] tokens = tokenService.reissueTokens("old-refresh-token");

        assertThat(tokens[0]).isEqualTo("new-access-token");
        assertThat(tokens[1]).isEqualTo("new-refresh-token");
        verify(refreshTokenRepository).deleteByUser(user);
    }

    @Test
    @DisplayName("토큰 재발급 실패 - DB에 없는 Refresh Token이면 AUTH_INVALID_REFRESH_TOKEN 예외가 발생한다")
    void reissueTokens_notFoundInDb_throwsException() {
        given(refreshTokenRepository.findByToken("unknown-token")).willReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.reissueTokens("unknown-token"))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_INVALID_REFRESH_TOKEN));
    }

    @Test
    @DisplayName("토큰 재발급 실패 - 만료된 Refresh Token이면 AUTH_INVALID_REFRESH_TOKEN 예외가 발생한다")
    void reissueTokens_expiredInDb_throwsException() throws Exception {
        User user = createUser();
        RefreshToken expired = createRefreshToken(user, "expired-token",
                LocalDateTime.now().minusDays(1));

        given(refreshTokenRepository.findByToken("expired-token"))
                .willReturn(Optional.of(expired));

        assertThatThrownBy(() -> tokenService.reissueTokens("expired-token"))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_INVALID_REFRESH_TOKEN));
        verify(refreshTokenRepository).delete(expired);
    }

    // ── logout ──────────────────────────────────────────────

    @Test
    @DisplayName("로그아웃 - 사용자 ID로 Refresh Token이 삭제된다")
    void logout_success() throws Exception {
        UUID userId = UUID.randomUUID();

        tokenService.logout(userId);

        verify(refreshTokenRepository).deleteByUserId(userId);
    }

    // ── 헬퍼 ──────────────────────────────────────────────

    private User createUser() throws Exception {
        User user = User.createEmailUser("test@devpick.kr", "encodedPw", "하영");
        Field idField = user.getClass().getSuperclass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, UUID.randomUUID());
        return user;
    }

    private RefreshToken createRefreshToken(User user, String token, LocalDateTime expiresAt) {
        return RefreshToken.builder()
                .user(user)
                .token(token)
                .expiresAt(expiresAt)
                .build();
    }
}
