package com.devpick.domain.community.dto;

import com.devpick.domain.community.entity.Post;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.global.util.MarkdownPreviewUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public record PostSummaryResponse(
        UUID id,
        String title,
        Level level,
        UUID authorId,
        String authorNickname,
        Job authorJob,
        String authorProfileImage,
        Instant createdAt,
        long answerCount,
        String contentPreview,
        String topAnswerPreview
) {
    private static final int CONTENT_PREVIEW_LENGTH = 150;
    private static final int ANSWER_PREVIEW_LENGTH = 100;

    public static PostSummaryResponse of(Post post, long answerCount, String topAnswerPreview) {
        String content = post.getContent();
        String plain = content != null ? MarkdownPreviewUtils.stripForPreview(content) : null;
        String contentPreview = plain != null && plain.length() > CONTENT_PREVIEW_LENGTH
                ? plain.substring(0, CONTENT_PREVIEW_LENGTH) + "..."
                : plain;

        return new PostSummaryResponse(
                post.getId(),
                post.getTitle(),
                post.getLevel(),
                post.getUser().getId(),
                post.getUser().getNickname(),
                post.getUser().getJob(),
                post.getUser().getProfileImage(),
                post.getCreatedAt() != null ? post.getCreatedAt().toInstant(ZoneOffset.UTC) : null,
                answerCount,
                contentPreview,
                topAnswerPreview
        );
    }

    public static String truncateAnswerPreview(String content) {
        if (content == null) return null;
        String plain = MarkdownPreviewUtils.stripForPreview(content);
        return plain.length() > ANSWER_PREVIEW_LENGTH
                ? plain.substring(0, ANSWER_PREVIEW_LENGTH) + "..."
                : plain;
    }
}
