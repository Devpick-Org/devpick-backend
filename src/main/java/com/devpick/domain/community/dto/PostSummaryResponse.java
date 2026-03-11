package com.devpick.domain.community.dto;

import com.devpick.domain.community.entity.Post;
import com.devpick.domain.user.entity.Level;

import java.time.LocalDateTime;
import java.util.UUID;

public record PostSummaryResponse(
        UUID id,
        String title,
        Level level,
        String authorNickname,
        LocalDateTime createdAt
) {
    public static PostSummaryResponse of(Post post) {
        return new PostSummaryResponse(
                post.getId(),
                post.getTitle(),
                post.getLevel(),
                post.getUser().getNickname(),
                post.getCreatedAt()
        );
    }
}
