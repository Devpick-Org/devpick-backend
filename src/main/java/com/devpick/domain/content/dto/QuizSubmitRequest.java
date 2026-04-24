package com.devpick.domain.content.dto;

import java.util.List;

public record QuizSubmitRequest(
        String level,
        int score,
        int totalQuestions,
        boolean passed,
        List<AnswerItem> answers
) {
    public record AnswerItem(
            String questionId,
            String selectedOptionId,
            String answerText,
            boolean isCorrect
    ) {}
}
