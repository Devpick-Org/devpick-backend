package com.devpick.domain.community.dto;

import com.devpick.domain.community.entity.AiAnswer;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public record AiAnswerResponse(
        UUID id,
        UUID postId,
        String content,
        List<String> keyPoints,
        List<String> suggestedTags,
        List<RelatedContentItem> relatedContents,
        Double confidence,
        Boolean isAdopted,
        Instant createdAt
) {
    public static AiAnswerResponse of(AiAnswer aiAnswer) {
        return new AiAnswerResponse(
                aiAnswer.getId(),
                aiAnswer.getPost().getId(),
                aiAnswer.getContent(),
                aiAnswer.getKeyPoints(),
                aiAnswer.getSuggestedTags(),
                aiAnswer.getRelatedContents(),
                aiAnswer.getConfidence(),
                aiAnswer.getIsAdopted(),
                aiAnswer.getCreatedAt() != null ? aiAnswer.getCreatedAt().toInstant(ZoneOffset.UTC) : null
        );
    }
}
