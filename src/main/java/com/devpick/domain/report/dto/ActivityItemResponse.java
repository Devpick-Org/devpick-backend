package com.devpick.domain.report.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code GET /history/activity} — 활동 피드 (content_liked 포함). points/answer 필드는 제외.
 */
public record ActivityItemResponse(
        UUID id,
        String actionType,
        HistoryItemResponse.ContentInfo content,
        HistoryItemResponse.PostInfo post,
        Instant createdAt
) {
    public static ActivityItemResponse from(HistoryItemResponse h) {
        return new ActivityItemResponse(
                h.id(),
                h.actionType(),
                h.content(),
                h.post(),
                h.createdAt()
        );
    }
}
