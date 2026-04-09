package com.devpick.domain.community.dto;

import com.devpick.domain.community.entity.Post;
import com.devpick.domain.user.entity.Level;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public record PostSummaryResponse(
        UUID id,
        String title,
        Level level,
        UUID authorId,
        String authorNickname,
        String authorProfileImage,
        Instant createdAt
) {
    public static PostSummaryResponse of(Post post) {
        return new PostSummaryResponse(
                post.getId(),
                post.getTitle(),
                post.getLevel(),
                post.getUser().getId(),
                post.getUser().getNickname(),
                post.getUser().getProfileImage(),
                post.getCreatedAt() != null ? post.getCreatedAt().toInstant(ZoneOffset.UTC) : null
        );
    }
}
