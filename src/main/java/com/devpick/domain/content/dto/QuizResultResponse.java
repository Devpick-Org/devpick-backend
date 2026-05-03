package com.devpick.domain.content.dto;

import com.devpick.domain.content.document.AiQuizDocument;

import java.util.List;
import java.util.UUID;

public record QuizResultResponse(
        UUID attemptId,
        UUID contentId,
        int score,
        int totalQuestions,
        boolean passed,
        int pointsEarned,
        int passingCount,
        List<AiQuizDocument.Question> questions,
        List<MyAnswer> myAnswers
) {
    public record MyAnswer(
            String questionId,
            String selectedOptionId,
            String answerText,
            boolean isCorrect
    ) {}
}
