package com.devpick.domain.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiSummaryResult(
        @JsonProperty("core_summary") String coreSummary,
        @JsonProperty("key_points") List<String> keyPoints,
        @JsonProperty("keywords") List<String> keywords,
        @JsonProperty("difficulty") String difficulty,
        @JsonProperty("next_recommendation") String nextRecommendation,
        @JsonProperty("confidence") Double confidence,
        @JsonProperty("additional_questions") List<String> additionalQuestions
) {}
