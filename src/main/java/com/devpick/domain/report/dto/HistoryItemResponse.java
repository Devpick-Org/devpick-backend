package com.devpick.domain.report.dto;

import com.devpick.domain.report.entity.History;

import java.time.LocalDateTime;
import java.util.UUID;

public record HistoryItemResponse(
        UUID id,
        String actionType,
        ContentInfo content,
        PostInfo post,
        AnswerInfo answer,
        LocalDateTime createdAt
) {
    public record ContentInfo(UUID id, String title, String preview) {}
    public record PostInfo(UUID id, String title) {}
    public record AnswerInfo(UUID id) {}

    public static HistoryItemResponse of(History history) {
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

        AnswerInfo answerInfo = history.getAnswer() != null
                ? new AnswerInfo(history.getAnswer().getId())
                : null;

        return new HistoryItemResponse(
                history.getId(),
                history.getActionType(),
                contentInfo,
                postInfo,
                answerInfo,
                history.getCreatedAt()
        );
    }
}
