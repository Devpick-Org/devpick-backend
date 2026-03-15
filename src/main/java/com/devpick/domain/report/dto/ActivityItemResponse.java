package com.devpick.domain.report.dto;

import com.devpick.domain.report.entity.History;

import java.time.LocalDateTime;
import java.util.UUID;

// DP-249: 내 활동 내역 조회 응답 아이템 (content_liked 포함)
public record ActivityItemResponse(
        UUID id,
        String activityType,
        ContentInfo content,
        PostInfo post,
        LocalDateTime createdAt
) {
    public record ContentInfo(UUID id, String title, String preview) {}
    public record PostInfo(UUID id, String title) {}

    public static ActivityItemResponse of(History history) {
        ContentInfo contentInfo = history.getContent() != null
                ? new ContentInfo(
                        history.getContent().getId(),
                        history.getContent().getTitle(),
                        history.getContent().getPreview())
                : null;

        PostInfo postInfo = history.getPost() != null
                ? new PostInfo(
                        history.getPost().getId(),
                        history.getPost().getTitle())
                : null;

        return new ActivityItemResponse(
                history.getId(),
                history.getActionType(),
                contentInfo,
                postInfo,
                history.getCreatedAt()
        );
    }
}
