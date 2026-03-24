package com.devpick.domain.community.dto;

import com.devpick.domain.community.entity.Post;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;

import java.time.LocalDateTime;
import java.util.UUID;

public record PostDetailResponse(
        UUID id,
        String title,
        String content,
        Level level,
        UUID authorId,
        String authorNickname,
        Job authorJob,
        Level authorLevel,
        long answerCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PostDetailResponse of(Post post, long answerCount) {
        return new PostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getLevel(),
                post.getUser().getId(),
                post.getUser().getNickname(),
                post.getUser().getJob(),
                post.getUser().getLevel(),
                answerCount,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
