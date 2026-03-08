package com.devpick.domain.user.dto;

import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.entity.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record UserProfileResponse(
        UUID userId,
        String email,
        String nickname,
        String profileImage,
        Job job,
        Level level,
        List<String> tags,
        LocalDateTime createdAt
) {
    public static UserProfileResponse from(User user) {
        List<String> tagNames = user.getUserTags().stream()
                .map(ut -> ut.getTag().getName())
                .toList();
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImage(),
                user.getJob(),
                user.getLevel(),
                tagNames,
                user.getCreatedAt()
        );
    }
}
