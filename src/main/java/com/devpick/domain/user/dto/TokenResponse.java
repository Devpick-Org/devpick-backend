package com.devpick.domain.user.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {
}
