package com.devpick.domain.community.dto;

import com.devpick.domain.community.entity.Answer;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public record AnswerResponse(
        UUID id,
        UUID postId,
        String content,
        Boolean isAdopted,
        UUID authorId,
        String authorNickname,
        Job authorJob,
        Level authorLevel,
        Instant createdAt,
        Instant updatedAt
) {
    public static AnswerResponse of(Answer answer) {
        return new AnswerResponse(
                answer.getId(),
                answer.getPost().getId(),
                answer.getContent(),
                answer.getIsAdopted(),
                answer.getUser().getId(),
                answer.getUser().getNickname(),
                answer.getUser().getJob(),
                answer.getUser().getLevel(),
                answer.getCreatedAt() != null ? answer.getCreatedAt().toInstant(ZoneOffset.UTC) : null,
                answer.getUpdatedAt() != null ? answer.getUpdatedAt().toInstant(ZoneOffset.UTC) : null
        );
    }
}
