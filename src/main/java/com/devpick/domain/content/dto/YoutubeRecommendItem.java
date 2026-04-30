package com.devpick.domain.content.dto;

import com.devpick.domain.content.entity.Content;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record YoutubeRecommendItem(
        UUID contentId,
        String title,
        String translatedTitle,
        String videoId,
        String channelName,
        String duration,
        String thumbnailUrl,
        List<String> tags,
        Instant publishedAt,
        boolean isScrapped,
        boolean isLiked
) {
    public static YoutubeRecommendItem of(Content content, boolean isLiked, Map<String, Object> extra) {
        List<String> tags = content.getContentTags().stream()
                .map(ct -> ct.getTag().getName())
                .toList();
        return new YoutubeRecommendItem(
                content.getId(),
                content.getTitle(),
                content.getTranslatedTitle(),
                (String) extra.get("videoId"),
                (String) extra.get("channelName"),
                (String) extra.get("duration"),
                content.getThumbnailUrl(),
                tags,
                content.getPublishedAt() != null ? content.getPublishedAt().toInstant(ZoneOffset.UTC) : null,
                false,
                isLiked
        );
    }
}