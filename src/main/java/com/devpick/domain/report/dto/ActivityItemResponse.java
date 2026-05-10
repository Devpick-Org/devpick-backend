package com.devpick.domain.report.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code GET /history/activity} — 활동 피드 (content_liked 포함).
 */
public record ActivityItemResponse(
        UUID id,
        String actionType,
        Integer points,
        HistoryItemResponse.ContentInfo content,
        HistoryItemResponse.PostInfo post,
        HistoryItemResponse.AnswerInfo answer,
        HistoryItemResponse.CommentInfo comment,
        HistoryItemResponse.JobPostingInfo jobPosting,
        Instant createdAt
) {
    public static ActivityItemResponse from(HistoryItemResponse h) {
        return new ActivityItemResponse(
                h.id(),
                h.actionType(),
                h.points(),
                h.content(),
                h.post(),
                h.answer(),
                h.comment(),
                h.jobPosting(),
                h.createdAt()
        );
    }
}
