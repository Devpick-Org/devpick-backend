package com.devpick.domain.report.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code GET /history/activity} — 활동 피드 (content_liked 포함). points 필드는 제외.
 */
public record ActivityItemResponse(
        UUID id,
        String actionType,
        HistoryItemResponse.ContentInfo content,
        HistoryItemResponse.PostInfo post,
        HistoryItemResponse.AnswerInfo answer,
        HistoryItemResponse.CommentInfo comment,
        Instant createdAt
) {
    public static ActivityItemResponse from(HistoryItemResponse h) {
        return new ActivityItemResponse(
                h.id(),
                h.actionType(),
                h.content(),
                h.post(),
                h.answer(),
                h.comment(),
                h.createdAt()
        );
    }
}
