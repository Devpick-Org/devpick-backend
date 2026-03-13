package com.devpick.domain.content.collector;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * AI 레포(FastAPI)가 POST /internal/contents 로 전송하는 정규화된 콘텐츠 DTO.
 * DP-289: 백엔드-AI 내부 API 협의 스키마 기준.
 */
public record NormalizedContentDto(
        @JsonProperty("source_name") String sourceName,
        String title,
        @JsonProperty("canonical_url") String canonicalUrl,
        @JsonProperty("published_at") String publishedAt,
        String preview,
        @JsonProperty("body_candidate") String bodyCandidate,
        @JsonProperty("body_source") String bodySource,
        @JsonProperty("content_kind") String contentKind,
        @JsonProperty("entry_external_id") String entryExternalId
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
