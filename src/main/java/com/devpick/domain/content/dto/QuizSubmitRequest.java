package com.devpick.domain.content.dto;

public record QuizSubmitRequest(
        String level,
        int score,
        int totalQuestions,
        boolean passed
) {}
