package com.devpick.domain.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * POST /internal/quiz 응답 역직렬화 DTO.
 * AI 서버의 AllLevelsQuizResponse 구조와 대응한다.
 */
public record AiQuizResult(
        @JsonProperty("content_id") String contentId,
        @JsonProperty("quiz_id") String quizId,
        @JsonProperty("title") String title,
        @JsonProperty("beginner") LevelQuiz beginner,
        @JsonProperty("junior") LevelQuiz junior,
        @JsonProperty("mid") LevelQuiz mid,
        @JsonProperty("senior") LevelQuiz senior,
        @JsonProperty("generated_at") String generatedAt
) {

    public record LevelQuiz(
            @JsonProperty("questions") List<QuestionResult> questions,
            @JsonProperty("passing_count") int passingCount,
            @JsonProperty("estimated_minutes") int estimatedMinutes
    ) {}

    public record QuestionResult(
            @JsonProperty("id") String id,
            @JsonProperty("type") String type,
            @JsonProperty("question") String question,
            @JsonProperty("options") List<OptionResult> options,
            @JsonProperty("correct_option_id") String correctOptionId,
            @JsonProperty("explanation") String explanation,
            @JsonProperty("correct_answer") String correctAnswer
    ) {}

    public record OptionResult(
            @JsonProperty("id") String id,
            @JsonProperty("text") String text
    ) {}

    /** AI 서버 level 키(beginner/junior/mid/senior)로 해당 레벨 퀴즈를 반환한다. */
    public LevelQuiz levelQuiz(String aiLevel) {
        return switch (aiLevel) {
            case "beginner" -> beginner;
            case "junior" -> junior;
            case "mid" -> mid;
            case "senior" -> senior;
            default -> junior;
        };
    }
}
