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
        QuizData quiz,
        List<MyAnswer> myAnswers
) {
    public record QuizData(
            List<AiQuizDocument.Question> questions,
            int passingCount
    ) {}

    public record MyAnswer(
            String questionId,
            String selectedOptionId,
            String answerText,
            boolean isCorrect
    ) {}
}
