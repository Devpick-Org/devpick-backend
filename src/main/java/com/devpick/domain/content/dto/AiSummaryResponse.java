package com.devpick.domain.content.dto;

import com.devpick.domain.content.document.AiSummaryDocument;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

public record AiSummaryResponse(
        String contentId,
        String level,
        String coreSummary,
        List<String> keyPoints,
        List<String> keywords,
        String difficulty,
        String nextRecommendation,
        Double confidence,
        List<String> additionalQuestions,
        Instant cachedAt,
        Instant expiresAt
) {
    public static AiSummaryResponse of(AiSummaryDocument doc) {
        return new AiSummaryResponse(
                doc.getContentId(),
                doc.getLevel(),
                doc.getCoreSummary(),
                doc.getKeyPoints(),
                doc.getKeywords(),
                doc.getDifficulty(),
                doc.getNextRecommendation(),
                doc.getConfidence(),
                doc.getAdditionalQuestions(),
                doc.getCachedAt() != null ? doc.getCachedAt().toInstant(ZoneOffset.UTC) : null,
                doc.getExpiresAt() != null ? doc.getExpiresAt().toInstant(ZoneOffset.UTC) : null
        );
    }
}
