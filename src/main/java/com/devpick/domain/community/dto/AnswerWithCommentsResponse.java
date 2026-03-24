package com.devpick.domain.community.dto;

import com.devpick.domain.community.entity.Answer;
import com.devpick.domain.community.entity.Comment;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AnswerWithCommentsResponse(
        UUID id,
        String content,
        UUID authorId,
        String authorNickname,
        Job authorJob,
        Level authorLevel,
        Boolean isAdopted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
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
                answer.getIsAdopted(),
                answer.getCreatedAt(),
                answer.getUpdatedAt(),
                comments.stream().map(CommentResponse::of).toList()
        );
    }
}
