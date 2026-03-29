package com.devpick.domain.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiQuizResult(
        @JsonProperty("questions") List<QuestionResult> questions,
        @JsonProperty("passing_count") int passingCount,
        @JsonProperty("estimated_minutes") int estimatedMinutes
) {

    public record QuestionResult(
            @JsonProperty("id") String id,
            @JsonProperty("question") String question,
            @JsonProperty("options") List<OptionResult> options,
            @JsonProperty("correct_option_id") String correctOptionId,
            @JsonProperty("explanation") String explanation
    ) {}

    public record OptionResult(
            @JsonProperty("id") String id,
            @JsonProperty("text") String text
    ) {}
}
