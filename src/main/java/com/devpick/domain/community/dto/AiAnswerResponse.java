package com.devpick.domain.community.dto;

import com.devpick.domain.community.entity.AiAnswer;

import java.time.LocalDateTime;
import java.util.UUID;

public record AiAnswerResponse(
        UUID id,
        UUID postId,
        String content,
        Boolean isAdopted,
        LocalDateTime createdAt
) {
    public static AiAnswerResponse of(AiAnswer aiAnswer) {
        return new AiAnswerResponse(
                aiAnswer.getId(),
                aiAnswer.getPost().getId(),
                aiAnswer.getContent(),
                aiAnswer.getIsAdopted(),
                aiAnswer.getCreatedAt()
        );
    }
}
