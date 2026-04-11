package com.devpick.domain.content.collector;

import com.devpick.domain.content.dto.StackOverflowAnswerDto;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * AI 레포(FastAPI)가 POST /internal/contents 로 전송하는 정규화된 콘텐츠 DTO.
 * DP-289: 백엔드-AI 내부 API 협의 스키마 기준.
 */
public record NormalizedContentDto(
        @JsonProperty("source_name") String sourceName,
        String title,
        String author,
        @JsonProperty("canonical_url") String canonicalUrl,
        @JsonProperty("published_at") String publishedAt,
        String preview,
        @JsonProperty("body_candidate") String bodyCandidate,
        @JsonProperty("is_original_visible") boolean isOriginalVisible,
        @JsonProperty("thumbnail_url") String thumbnailUrl,
        @JsonProperty("thumbnail_width") Integer thumbnailWidth,
        @JsonProperty("thumbnail_height") Integer thumbnailHeight,
        @JsonProperty("license_type") String licenseType,
        List<String> tags,
        // 소스별 참여 지표 (null for RSS sources)
        Integer score,                                            // SO 전용: 추천 순점수 (음수 가능)
        Integer likes,                                            // Velog 전용: 좋아요 수
        @JsonProperty("view_count") Integer viewCount,           // SO 전용: 조회수
        @JsonProperty("comments_count") Integer commentsCount,   // Velog 전용: 댓글 수
        // Stack Overflow 전용 필드
        @JsonProperty("is_answered") Boolean isAnswered,
        @JsonProperty("question_content") String questionContent,
        @JsonProperty("accepted_answer") StackOverflowAnswerDto acceptedAnswer,
        @JsonProperty("top_answers") List<StackOverflowAnswerDto> topAnswers
) {

    /**
     * publishedAt (ISO 8601 String, nullable) → LocalDateTime 변환.
     * AI 레포는 UTC offset 포함 형식(예: "2026-03-10T09:00:00Z")으로 전송하므로
     * OffsetDateTime으로 파싱 후 LocalDateTime으로 변환한다.
     * 파싱 실패 시 null 반환.
     */
    public LocalDateTime parsedPublishedAt() {
        if (publishedAt == null || publishedAt.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(publishedAt).toLocalDateTime();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(publishedAt);
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
    }
}
