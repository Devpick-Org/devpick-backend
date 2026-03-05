package com.devpick.domain.user.dto;

import com.devpick.domain.user.entity.User;

import java.util.UUID;

public record SignupResponse(
        UUID userId,
        String email,
        String nickname
) {
    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getEmail(), user.getNickname());
    }
}
