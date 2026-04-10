package com.devpick.domain.content.dto;

import com.devpick.domain.content.entity.Content;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public record ContentDetailResponse(
        UUID id,
        String title,
        String author,
        String sourceName,
        String preview,
        String thumbnailUrl,
        Integer thumbnailWidth,
        Integer thumbnailHeight,
        String canonicalUrl,
        String originalContent,
        boolean isOriginalVisible,
        String licenseType,
        Instant publishedAt,
        List<String> tags,
        boolean isScrapped,
        boolean isLiked,
        // Stack Overflow 전용 필드 (비-SO 소스는 null)
        Integer score,
        Integer viewCount,
        Boolean isAnswered,
        String questionContent,
        StackOverflowAnswerDto acceptedAnswer,
        List<StackOverflowAnswerDto> topAnswers
) {
    public static ContentDetailResponse of(Content content, boolean isScrapped, boolean isLiked) {
        List<String> tags = content.getContentTags().stream()
                .map(ct -> ct.getTag().getName())
                .toList();
        return new ContentDetailResponse(
                content.getId(),
                content.getTitle(),
                content.getAuthor(),
                content.getSource().getName(),
                content.getPreview(),
                content.getThumbnailUrl(),
                content.getThumbnailWidth(),
                content.getThumbnailHeight(),
                content.getCanonicalUrl(),
                content.getIsOriginalVisible() ? content.getOriginalContent() : null,
                content.getIsOriginalVisible(),
                content.getLicenseType(),
                content.getPublishedAt() != null ? content.getPublishedAt().toInstant(ZoneOffset.UTC) : null,
                tags,
                isScrapped,
                isLiked,
                content.getScore(),
                content.getViewCount(),
                content.getIsAnswered(),
                content.getQuestionContent(),
                content.getAcceptedAnswer(),
                content.getTopAnswers()
        );
    }
}
