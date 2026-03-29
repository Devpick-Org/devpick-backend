package com.devpick.domain.content.dto;

public record QuizSubmitResponse(
        boolean passed,
        int score,
        int totalQuestions,
        int pointsEarned
) {}
