package com.devpick.domain.community.dto;

import com.devpick.domain.community.entity.Answer;
import com.devpick.domain.community.entity.Comment;
import com.devpick.domain.subscription.entity.PlanType;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public record AnswerWithCommentsResponse(
        UUID id,
        String content,
        UUID authorId,
        String authorNickname,
        Job authorJob,
        Level authorLevel,
        String authorProfileImage,
        PlanType authorPlanType,
        Boolean isAdopted,
        Boolean isEdited,
        Boolean canAdopt,
        Instant createdAt,
        Instant updatedAt,
        List<CommentResponse> comments
) {
    public static AnswerWithCommentsResponse of(Answer answer, List<Comment> comments,
                                                UUID currentUserId, UUID postAuthorId, boolean anyAdopted) {
        boolean canAdopt = postAuthorId.equals(currentUserId)
                && !anyAdopted
                && !Boolean.TRUE.equals(answer.getIsAdopted())
                && !answer.getUser().getId().equals(currentUserId);
        return new AnswerWithCommentsResponse(
                answer.getId(),
                answer.getContent(),
                answer.getUser().getId(),
                answer.getUser().getNickname(),
                answer.getUser().getJob(),
                answer.getUser().getLevel(),
                answer.getUser().getProfileImage(),
                answer.getUser().getPlanType(),
                answer.getIsAdopted(),
                answer.getIsEdited(),
                canAdopt,
                answer.getCreatedAt() != null ? answer.getCreatedAt().toInstant(ZoneOffset.UTC) : null,
                answer.getUpdatedAt() != null ? answer.getUpdatedAt().toInstant(ZoneOffset.UTC) : null,
                comments.stream().map(CommentResponse::of).toList()
        );
    }
}
