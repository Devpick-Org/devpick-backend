package com.devpick.domain.content.dto;

import com.devpick.domain.content.entity.Content;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public record ContentSummaryResponse(
        UUID id,
        String title,
        String author,
        String sourceName,
        String preview,
        String thumbnailUrl,
        String canonicalUrl,
        List<String> tags,
        Instant publishedAt,
        boolean isScrapped,
        boolean isLiked,
        // Stack Overflow 전용 필드 (비-SO 소스는 null)
        Integer score,
        Integer viewCount,
        Boolean isAnswered
) {
    public static ContentSummaryResponse of(Content content, boolean isScrapped, boolean isLiked) {
        List<String> tags = content.getContentTags().stream()
                .map(ct -> ct.getTag().getName())
                .toList();
        return new ContentSummaryResponse(
                content.getId(),
                content.getTitle(),
                content.getAuthor(),
                content.getSource().getName(),
                content.getPreview(),
                content.getThumbnailUrl(),
                content.getCanonicalUrl(),
                tags,
                content.getPublishedAt() != null ? content.getPublishedAt().toInstant(ZoneOffset.UTC) : null,
                isScrapped,
                isLiked,
                content.getScore(),
                content.getViewCount(),
                content.getIsAnswered()
        );
    }
}
