package com.devpick.domain.report.dto;

import com.devpick.domain.point.entity.PointAction;
import com.devpick.domain.report.entity.History;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public record HistoryItemResponse(
        UUID id,
        String actionType,
        Integer points,
        ContentInfo content,
        PostInfo post,
        AnswerInfo answer,
        Instant createdAt
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

        Integer points = switch (history.getActionType()) {
            case "ai_summary_viewed" -> PointAction.AI_SUMMARY_VIEW.getPoints();
            case "scrapped"          -> PointAction.CONTENT_SCRAP.getPoints();
            case "content_liked"     -> PointAction.CONTENT_LIKE.getPoints();
            case "question_created"  -> PointAction.QUESTION_WRITE.getPoints();
            case "answer_written"    -> PointAction.ANSWER_WRITE.getPoints();
            case "answer_adopted"    -> PointAction.ANSWER_ADOPTED.getPoints();
            case "daily_login"       -> PointAction.DAILY_LOGIN.getPoints();
            case "ai_quiz_completed" -> PointAction.AI_QUIZ_PASS.getPoints();
            default                  -> null;
        };

        return new HistoryItemResponse(
                history.getId(),
                history.getActionType(),
                points,
                contentInfo,
                postInfo,
                answerInfo,
                history.getCreatedAt() != null ? history.getCreatedAt().toInstant(ZoneOffset.UTC) : null
        );
    }
}
