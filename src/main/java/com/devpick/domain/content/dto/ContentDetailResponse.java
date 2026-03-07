package com.devpick.domain.content.dto;

import com.devpick.domain.content.entity.Content;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ContentDetailResponse(
        UUID id,
        String title,
        String author,
        String preview,
        String canonicalUrl,
        String originalContent,
        boolean isOriginalVisible,
        String licenseType,
        LocalDateTime publishedAt,
        List<String> tags,
        boolean isScrapped,
        boolean isLiked,
        String sourceName
) {
    public static ContentDetailResponse of(Content content, boolean isScrapped, boolean isLiked) {
        List<String> tags = content.getContentTags().stream()
                .map(ct -> ct.getTag().getName())
                .toList();
        return new ContentDetailResponse(
                content.getId(),
                content.getTitle(),
                content.getAuthor(),
                content.getPreview(),
                content.getCanonicalUrl(),
                content.getOriginalContent(),
                content.getIsOriginalVisible(),
                content.getLicenseType(),
                content.getPublishedAt(),
                tags,
                isScrapped,
                isLiked,
                content.getSource().getName()
        );
    }
}
