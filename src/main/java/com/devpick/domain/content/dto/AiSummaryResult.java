package com.devpick.domain.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * POST /internal/summaries 응답 역직렬화 DTO.
 * AI 서버의 AllLevelsSummaryResponse 구조와 대응한다.
 */
public record AiSummaryResult(
        @JsonProperty("content_id") String contentId,
        @JsonProperty("common") CommonSummary common,
        @JsonProperty("beginner") LevelSummary beginner,
        @JsonProperty("junior") LevelSummary junior,
        @JsonProperty("mid") LevelSummary mid,
        @JsonProperty("senior") LevelSummary senior,
        @JsonProperty("generated_at") String generatedAt,
        @JsonProperty("thumbnail_url") String thumbnailUrl
) {

    public record CommonSummary(
            @JsonProperty("one_line_summary") String oneLineSummary,
            @JsonProperty("keywords") List<String> keywords,
            @JsonProperty("category") String category,
            @JsonProperty("tags") List<String> tags,
            @JsonProperty("difficulty") String difficulty
    ) {}

    public record LevelSummary(
            @JsonProperty("core_summary") String coreSummary,
            @JsonProperty("key_points") List<String> keyPoints,
            @JsonProperty("additional_questions") List<String> additionalQuestions,
            @JsonProperty("next_recommendation") String nextRecommendation,
            @JsonProperty("confidence") Double confidence
    ) {}

    /** AI 서버 level 키(beginner/junior/mid/senior)로 해당 레벨 요약을 반환한다. */
    public LevelSummary levelSummary(String aiLevel) {
        return switch (aiLevel) {
            case "beginner" -> beginner;
            case "junior" -> junior;
            case "mid" -> mid;
            case "senior" -> senior;
            default -> junior;
        };
    }
}
