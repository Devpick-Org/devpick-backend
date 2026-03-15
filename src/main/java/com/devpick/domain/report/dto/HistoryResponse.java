package com.devpick.domain.report.dto;

import com.devpick.domain.report.entity.History;

import java.time.LocalDateTime;
import java.util.UUID;

public record HistoryResponse(
        UUID id,
        String actionType,
        UUID contentId,
        String contentTitle,
        UUID postId,
        String postTitle,
        LocalDateTime createdAt
) {
    public static HistoryResponse of(History history) {
        return new HistoryResponse(
                history.getId(),
                history.getActionType(),
                history.getContent() != null ? history.getContent().getId() : null,
                history.getContent() != null ? history.getContent().getTitle() : null,
                history.getPost() != null ? history.getPost().getId() : null,
                history.getPost() != null ? history.getPost().getTitle() : null,
                history.getCreatedAt()
        );
    }
}
