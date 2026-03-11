package com.devpick.domain.community.dto;

import com.devpick.domain.community.entity.Answer;

import java.time.LocalDateTime;
import java.util.UUID;

public record AnswerResponse(
        UUID id,
        UUID postId,
        String content,
        Boolean isAdopted,
        UUID authorId,
        String authorNickname,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AnswerResponse of(Answer answer) {
        return new AnswerResponse(
                answer.getId(),
                answer.getPost().getId(),
                answer.getContent(),
                answer.getIsAdopted(),
                answer.getUser().getId(),
                answer.getUser().getNickname(),
                answer.getCreatedAt(),
                answer.getUpdatedAt()
        );
    }
}
