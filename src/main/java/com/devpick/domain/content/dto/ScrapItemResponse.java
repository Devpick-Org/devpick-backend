package com.devpick.domain.content.dto;

import com.devpick.domain.content.entity.Scrap;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

public record ScrapItemResponse(
        UUID id,
        UUID contentId,
        String title,
        String sourceName,
        String thumbnail,
        String summary,
        Instant createdAt
) {
    public static ScrapItemResponse of(Scrap scrap, Map<UUID, String> summaryMap) {
        UUID contentId = scrap.getContent().getId();
        String raw = summaryMap.getOrDefault(contentId, scrap.getContent().getPreview());
        String summary = (raw != null && !raw.isBlank()) ? raw : null;
        return new ScrapItemResponse(
                scrap.getId(),
                contentId,
                scrap.getContent().getTitle(),
                scrap.getContent().getSource().getName(),
                scrap.getContent().getThumbnailUrl(),
                summary,
                scrap.getCreatedAt().toInstant(ZoneOffset.UTC)
        );
    }
}
