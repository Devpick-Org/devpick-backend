package com.devpick.domain.user.dto;

import com.devpick.domain.user.entity.User;

import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        UUID userId,
        String email,
        String nickname
) {
    public static LoginResponse of(String accessToken, String refreshToken, User user) {
        return new LoginResponse(accessToken, refreshToken, user.getId(), user.getEmail(), user.getNickname());
    }
}
