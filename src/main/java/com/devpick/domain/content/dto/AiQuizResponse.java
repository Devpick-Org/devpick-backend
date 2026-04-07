package com.devpick.domain.content.dto;

import com.devpick.domain.content.document.AiQuizDocument;
import com.devpick.domain.content.entity.QuizAttempt;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

public record AiQuizResponse(
        String contentId,
        String title,
        String level,
        List<Question> questions,
        int passingCount,
        int estimatedMinutes,
        Instant cachedAt,
        Instant expiresAt,
        boolean hasAttempted,
        Boolean lastPassed,
        Integer lastScore,
        Integer lastTotalQuestions
) {

    public record Question(
            String id,
            String question,
            List<Option> options,
            String correctOptionId,
            String explanation
    ) {}

    public record Option(
            String id,
            String text
    ) {}

    public static AiQuizResponse of(AiQuizDocument doc, QuizAttempt lastAttempt) {
        List<Question> questions = doc.getQuestions().stream()
                .map(q -> new Question(
                        q.getId(),
                        q.getQuestion(),
                        q.getOptions().stream()
                                .map(o -> new Option(o.getId(), o.getText()))
                                .toList(),
                        q.getCorrectOptionId(),
                        q.getExplanation()
                ))
                .toList();

        boolean hasAttempted = lastAttempt != null;
        return new AiQuizResponse(
                doc.getContentId(),
                doc.getTitle(),
                doc.getLevel(),
                questions,
                doc.getPassingCount(),
                doc.getEstimatedMinutes(),
                doc.getCachedAt() != null ? doc.getCachedAt().toInstant(ZoneOffset.UTC) : null,
                doc.getExpiresAt() != null ? doc.getExpiresAt().toInstant(ZoneOffset.UTC) : null,
                hasAttempted,
                hasAttempted ? lastAttempt.isPassed() : null,
                hasAttempted ? lastAttempt.getScore() : null,
                hasAttempted ? lastAttempt.getTotalQuestions() : null
        );
    }
}
