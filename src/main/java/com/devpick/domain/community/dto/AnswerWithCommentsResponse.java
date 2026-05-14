package com.devpick.domain.community.dto;

import com.devpick.domain.community.entity.Answer;
import com.devpick.domain.community.entity.Comment;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public record AnswerWithCommentsResponse(
        UUID id,
        String content,
        UUID authorId,
        String authorNickname,
        Job authorJob,
        Level authorLevel,
        String authorProfileImage,
        Boolean isAdopted,
        Boolean isEdited,
        Instant createdAt,
        Instant updatedAt,
        List<CommentResponse> comments
) {
    public static AnswerWithCommentsResponse of(Answer answer, List<Comment> comments) {
        return new AnswerWithCommentsResponse(
                answer.getId(),
                answer.getContent(),
                answer.getUser().getId(),
                answer.getUser().getNickname(),
                answer.getUser().getJob(),
                answer.getUser().getLevel(),
                answer.getUser().getProfileImage(),
                answer.getIsAdopted(),
                answer.getIsEdited(),
                answer.getCreatedAt() != null ? answer.getCreatedAt().toInstant(ZoneOffset.UTC) : null,
                answer.getUpdatedAt() != null ? answer.getUpdatedAt().toInstant(ZoneOffset.UTC) : null,
                comments.stream().map(CommentResponse::of).toList()
        );
    }
}
