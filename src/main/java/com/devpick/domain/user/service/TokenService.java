package com.devpick.domain.user.service;

import com.devpick.domain.user.dto.TokenResponse;
import com.devpick.domain.user.entity.RefreshToken;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.RefreshTokenRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Refresh Token 재발급 서비스 (DP-181).
 * Token Rotation 방식: 재발급 시 기존 Refresh Token 삭제 + 새 토큰 발급.
 *
 * 확장 포인트 (DP-XXX): Redis 설정 완료 후 DB → Redis 방식 전환 고려
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Refresh Token으로 새 Access Token + Refresh Token 재발급.
     */
    @Transactional
    public TokenResponse reissueTokens(String refreshToken) {
        // 1. DB에 존재하는지 확인
        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new DevpickException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN));

        // 2. DB 만료 시각 검증
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(stored);
            throw new DevpickException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN);
        }

        User user = stored.getUser();

        // 3. 기존 토큰 삭제 + 신규 토큰 발급 (Token Rotation)
        refreshTokenRepository.deleteByUser(user);

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken();

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token(newRefreshToken)
                .expiresAt(jwtTokenProvider.getRefreshTokenExpiresAt())
                .build());

        return new TokenResponse(newAccessToken, newRefreshToken);
    }
}
