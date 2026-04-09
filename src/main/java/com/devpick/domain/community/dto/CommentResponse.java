package com.devpick.domain.community.dto;

import com.devpick.domain.community.entity.Comment;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        UUID answerId,
        UUID userId,
        String nickname,
        String profileImage,
        String content,
        Instant createdAt,
        Instant updatedAt
) {
    public static CommentResponse of(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getAnswer().getId(),
                comment.getUser().getId(),
                comment.getUser().getNickname(),
                comment.getUser().getProfileImage(),
                comment.getContent(),
                comment.getCreatedAt() != null ? comment.getCreatedAt().toInstant(ZoneOffset.UTC) : null,
                comment.getUpdatedAt() != null ? comment.getUpdatedAt().toInstant(ZoneOffset.UTC) : null
        );
    }
}
