package com.devpick.domain.content.dto;

import com.devpick.domain.content.entity.Content;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public record ContentSummaryResponse(
        UUID id,
        String title,
        String translatedTitle,
        String author,
        String sourceName,
        String preview,
        String thumbnailUrl,
        Integer thumbnailWidth,
        Integer thumbnailHeight,
        String canonicalUrl,
        List<String> tags,
        Instant publishedAt,
        boolean isScrapped,
        boolean isLiked,
        // 소스별 참여 지표 (null for RSS sources)
        Integer score,        // SO 전용: 추천 순점수
        Integer likes,        // Velog 전용: 좋아요 수
        Integer viewCount,    // SO 전용: 조회수
        Integer commentsCount, // Velog 전용: 댓글 수
        // Stack Overflow 전용 구조화 필드 (비-SO 소스는 null)
        Boolean isAnswered
) {
    public static ContentSummaryResponse of(Content content, boolean isScrapped, boolean isLiked) {
        return of(content, isScrapped, isLiked, content.getPreview());
    }

    public static ContentSummaryResponse of(Content content, boolean isScrapped, boolean isLiked, String preview) {
        List<String> tags = content.getContentTags().stream()
                .map(ct -> ct.getTag().getName())
                .toList();
        String sourceName = content.getSource().getName();
        boolean stackOverflow = ContentSourceNames.isStackOverflow(sourceName);
        return new ContentSummaryResponse(
                content.getId(),
                content.getTitle(),
                content.getTranslatedTitle(),
                content.getAuthor(),
                sourceName,
                preview,
                content.getThumbnailUrl(),
                content.getThumbnailWidth(),
                content.getThumbnailHeight(),
                content.getCanonicalUrl(),
                tags,
                content.getPublishedAt() != null ? content.getPublishedAt().toInstant(ZoneOffset.UTC) : null,
                isScrapped,
                isLiked,
                stackOverflow ? null : content.getScore(),
                content.getLikes(),
                stackOverflow ? null : content.getViewCount(),
                content.getCommentsCount(),
                stackOverflow ? null : content.getIsAnswered()
        );
    }
}
