package com.devpick.domain.content.dto;

import com.devpick.domain.content.entity.Content;

import java.time.LocalDateTime;
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
        LocalDateTime publishedAt,
        boolean isScrapped,
        boolean isLiked
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
                content.getPublishedAt(),
                isScrapped,
                isLiked
        );
    }
}
