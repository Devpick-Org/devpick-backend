package com.devpick.domain.content.dto;

import com.devpick.domain.content.entity.QuizAttempt;
import com.devpick.domain.content.service.AiSummaryService;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

public record QuizHistoryItemResponse(
        UUID attemptId,
        UUID contentId,
        String contentTitle,
        String thumbnail,
        String preview,
        String level,
        int score,
        int totalQuestions,
        boolean passed,
        Instant attemptedAt
) {
    public static QuizHistoryItemResponse of(QuizAttempt attempt, Map<String, String> previewMap) {
        UUID contentId = attempt.getContent().getId();
        String key = contentId + "|" + attempt.getLevel();
        String rawPreview = previewMap.get(key);
        String preview = (rawPreview != null && !rawPreview.isBlank()) ? rawPreview : null;

        return new QuizHistoryItemResponse(
                attempt.getId(),
                contentId,
                attempt.getContent().getTitle(),
                attempt.getContent().getThumbnailUrl(),
                preview,
                AiSummaryService.fromAiServerLevel(attempt.getLevel()),
                attempt.getScore(),
                attempt.getTotalQuestions(),
                attempt.isPassed(),
                attempt.getCreatedAt().toInstant(ZoneOffset.UTC)
        );
    }
}
