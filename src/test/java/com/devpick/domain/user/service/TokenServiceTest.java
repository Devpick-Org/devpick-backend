package com.devpick.domain.user.service;

import com.devpick.domain.user.dto.TokenResponse;
import com.devpick.domain.user.entity.RefreshToken;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.RefreshTokenRepository;
import com.devpick.domain.user.repository.UserRepository;
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

    @Mock
    private UserRepository userRepository;

    // ── reissueTokens ──────────────────────────────────────────────

    @Test
    @DisplayName("토큰 재발급 - 유효한 Refresh Token으로 새 토큰 쌍이 발급된다")
    void reissueTokens_success() throws Exception {
        // given
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

        // when
        TokenResponse response = tokenService.reissueTokens("old-refresh-token");

        // then
        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        verify(refreshTokenRepository).deleteByUser(user);
    }

    @Test
    @DisplayName("토큰 재발급 실패 - DB에 없는 Refresh Token이면 AUTH_INVALID_REFRESH_TOKEN 예외가 발생한다")
    void reissueTokens_notFoundInDb_throwsException() {
        // given
        given(refreshTokenRepository.findByToken("unknown-token")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> tokenService.reissueTokens("unknown-token"))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_INVALID_REFRESH_TOKEN));
    }

    @Test
    @DisplayName("토큰 재발급 실패 - 만료된 Refresh Token이면 AUTH_INVALID_REFRESH_TOKEN 예외가 발생한다")
    void reissueTokens_expiredInDb_throwsException() throws Exception {
        // given
        User user = createUser();
        RefreshToken expired = createRefreshToken(user, "expired-token",
                LocalDateTime.now().minusDays(1));

        given(refreshTokenRepository.findByToken("expired-token"))
                .willReturn(Optional.of(expired));

        // when & then
        assertThatThrownBy(() -> tokenService.reissueTokens("expired-token"))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_INVALID_REFRESH_TOKEN));
        verify(refreshTokenRepository).delete(expired);
    }

    // ── logout ──────────────────────────────────────────────

    @Test
    @DisplayName("로그아웃 - 유효한 사용자 ID로 Refresh Token이 삭제된다")
    void logout_success() throws Exception {
        // given
        User user = createUser();
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));

        // when
        tokenService.logout(user.getId());

        // then
        verify(refreshTokenRepository).deleteByUser(user);
    }

    @Test
    @DisplayName("로그아웃 실패 - 존재하지 않는 사용자 ID이면 AUTH_USER_NOT_FOUND 예외가 발생한다")
    void logout_userNotFound_throwsException() {
        // given
        UUID unknownId = UUID.randomUUID();
        given(userRepository.findById(unknownId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> tokenService.logout(unknownId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_USER_NOT_FOUND));
    }

    // ── 헬퍼 ──────────────────────────────────────────────

    private User createUser() throws Exception {
        User user = User.createEmailUser("test@devpick.kr", "encodedPw", "하영");
        // 리플렉션으로 id 주입 (UuidGenerator는 영속화 시 생성)
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
