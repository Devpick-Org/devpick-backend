package com.devpick.domain.user.dto;

import com.devpick.domain.user.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

/**
 * 소셜 로그인 응답 DTO (DP-183, DP-184, DP-284).
 * 일반 로그인과 달리 isNewUser 플래그를 포함한다.
 * 프론트가 isNewUser=true 시 온보딩 화면으로 분기한다.
 */
public record SocialLoginResponse(
        String accessToken,
        UUID userId,
        String email,
        String nickname,
        boolean isNewUser,
        @JsonIgnore String refreshTokenValue
) {
    public static SocialLoginResponse of(String accessToken, String refreshToken, User user, boolean isNewUser) {
        return new SocialLoginResponse(accessToken, user.getId(), user.getEmail(), user.getNickname(), isNewUser, refreshToken);
    }
}
