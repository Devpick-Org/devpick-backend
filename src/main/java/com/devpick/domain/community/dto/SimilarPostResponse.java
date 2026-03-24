package com.devpick.domain.community.dto;

import com.devpick.domain.community.entity.Post;
import com.devpick.domain.user.entity.Level;

import java.time.LocalDateTime;
import java.util.UUID;

public record SimilarPostResponse(
        UUID id,
        String title,
        Level level,
        long answerCount,
        LocalDateTime createdAt
) {
    public static SimilarPostResponse of(Post post, long answerCount) {
        return new SimilarPostResponse(
                post.getId(),
                post.getTitle(),
                post.getLevel(),
                answerCount,
                post.getCreatedAt()
        );
    }
}
