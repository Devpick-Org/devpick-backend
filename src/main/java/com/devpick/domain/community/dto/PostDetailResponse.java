package com.devpick.domain.community.dto;

import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.entity.PostAttachment;
import com.devpick.domain.community.entity.PostType;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public record PostDetailResponse(
        UUID id,
        PostType postType,
        String title,
        String content,
        Level level,
        UUID authorId,
        String authorNickname,
        Job authorJob,
        Level authorLevel,
        String authorProfileImage,
        long answerCount,
        Instant createdAt,
        Instant updatedAt,
        List<PostAttachmentDTO> attachments
) {
    public static PostDetailResponse of(Post post, long answerCount) {
        List<PostAttachmentDTO> attachmentDtos = post.getAttachments().stream()
                .map(PostDetailResponse::toAttachmentDto)
                .toList();
        return new PostDetailResponse(
                post.getId(),
                post.getPostType(),
                post.getTitle(),
                post.getContent(),
                post.getLevel(),
                post.getUser().getId(),
                post.getUser().getNickname(),
                post.getUser().getJob(),
                post.getUser().getLevel(),
                post.getUser().getProfileImage(),
                answerCount,
                post.getCreatedAt() != null ? post.getCreatedAt().toInstant(ZoneOffset.UTC) : null,
                post.getUpdatedAt() != null ? post.getUpdatedAt().toInstant(ZoneOffset.UTC) : null,
                attachmentDtos
        );
    }

    private static PostAttachmentDTO toAttachmentDto(PostAttachment a) {
        String type = a.getType() != null ? a.getType() : "file";
        return new PostAttachmentDTO(type, a.getUrl(), a.getFileName());
    }
}
